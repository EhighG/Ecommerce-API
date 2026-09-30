import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import {
  getCsrf,
  login,
  logout,
  logUnexpectedResponse,
  patchWithCsrfRetry,
  readApiError,
} from "../lib/auth.js";
import { env } from "../lib/env.js";

const BASE_URL = env("BASE_URL");
const USER_PASSWORD = env("PASSWORD");
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 100);
const WRONG_STATUS_CHANGE_CODE = 3003;

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function fetchSellerOrderedItems(sellerId) {
  const res = http.get(
    `${BASE_URL}/order-items?sellerId=${sellerId}&statusList=ORDERED&size=${PAGE_SIZE}`,
    { tags: { name: "GET /order-items" } },
  );
  const body = parseJson(res);
  const ok = check(res, {
    "seller order item search status is 200": (r) => r.status === 200,
    "seller order item search returns items": () => Array.isArray(body?.items),
  });

  if (!ok) {
    logUnexpectedResponse("seller order item search failed", res, { sellerId });
    fail("seller order item search failed");
  }

  return body;
}

function dedupeOrderItems(items) {
  const uniqueItems = [];
  const seen = new Set();

  for (const item of items) {
    if (seen.has(item.orderItemId)) {
      continue;
    }

    seen.add(item.orderItemId);
    uniqueItems.push(item);
  }

  return uniqueItems;
}

function isWrongStatusChange(res) {
  return (
    res.status === 400 && readApiError(res)?.code === WRONG_STATUS_CHANGE_CODE
  );
}

function fetchBuyerOrders() {
  const res = http.get(`${BASE_URL}/orders/me`, {
    tags: { name: "GET /orders/me" },
  });
  const body = parseJson(res);
  const ok = check(res, {
    "buyer order list status is 200": (r) => r.status === 200,
    "buyer order list is array": () => Array.isArray(body),
  });

  if (!ok) {
    logUnexpectedResponse("buyer order list failed", res);
    fail("buyer order list failed");
  }

  return body;
}

function fetchBuyerOrderItems(orderId, page) {
  const res = http.get(
    `${BASE_URL}/order-items?orderId=${orderId}&size=${PAGE_SIZE}&page=${page}`,
    { tags: { name: "GET /order-items" } },
  );
  const body = parseJson(res);
  const ok = check(res, {
    "buyer order item search status is 200": (r) => r.status === 200,
    "buyer order item search returns items": () => Array.isArray(body?.items),
  });

  if (!ok) {
    logUnexpectedResponse("buyer order item search failed", res, {
      orderId,
      page,
    });
    fail("buyer order item search failed");
  }

  return body;
}

function transitionSellerItems(seller, csrfRef, items) {
  const uniqueItems = dedupeOrderItems(items);

  for (const item of uniqueItems) {
    const shipRes = patchWithCsrfRetry(
      BASE_URL,
      `/order-items/${item.orderItemId}/ship`,
      csrfRef,
      { tags: { name: "PATCH /order-items/{id}/ship" } },
    );
    if (shipRes.status === 200) {
      check(shipRes, {
        "ship status is 200": (r) => r.status === 200,
      });
      continue;
    }

    if (isWrongStatusChange(shipRes)) {
      logUnexpectedResponse("ship skipped by status", shipRes, {
        sellerEmail: seller.email,
        orderItemId: item.orderItemId,
      });
      continue;
    }

    check(shipRes, {
      "ship status is 200": (r) => r.status === 200,
    });
    {
      logUnexpectedResponse("ship failed", shipRes, {
        sellerEmail: seller.email,
        orderItemId: item.orderItemId,
      });
      fail("ship failed");
    }
  }

  for (const item of uniqueItems) {
    const deliverRes = patchWithCsrfRetry(
      BASE_URL,
      `/order-items/${item.orderItemId}/deliver`,
      csrfRef,
      { tags: { name: "PATCH /order-items/{id}/deliver" } },
    );
    if (deliverRes.status === 200) {
      check(deliverRes, {
        "deliver status is 200": (r) => r.status === 200,
      });
      continue;
    }

    if (isWrongStatusChange(deliverRes)) {
      logUnexpectedResponse("deliver skipped by status", deliverRes, {
        sellerEmail: seller.email,
        orderItemId: item.orderItemId,
      });
      continue;
    }

    check(deliverRes, {
      "deliver status is 200": (r) => r.status === 200,
    });
    {
      logUnexpectedResponse("deliver failed", deliverRes, {
        sellerEmail: seller.email,
        orderItemId: item.orderItemId,
      });
      fail("deliver failed");
    }
  }
}

function collectDeliveredBuyerItems() {
  const orders = fetchBuyerOrders();
  const deliveredItems = [];

  for (const order of orders) {
    let page = 0;

    while (true) {
      const searchRes = fetchBuyerOrderItems(order.orderId, page);

      for (const item of searchRes.items) {
        if (item.status === "DELIVERED") {
          deliveredItems.push(item);
        }
      }

      if (!searchRes.hasNext) {
        break;
      }

      page += 1;
    }
  }

  return deliveredItems;
}

function confirmBuyerItems(buyer, csrfRef, items) {
  const uniqueItems = dedupeOrderItems(items);

  for (const item of uniqueItems) {
    const confirmRes = patchWithCsrfRetry(
      BASE_URL,
      `/order-items/${item.orderItemId}/confirm`,
      csrfRef,
      { tags: { name: "PATCH /order-items/{id}/confirm" } },
    );
    if (confirmRes.status === 200) {
      check(confirmRes, {
        "confirm status is 200": (r) => r.status === 200,
      });
      continue;
    }

    if (isWrongStatusChange(confirmRes)) {
      logUnexpectedResponse("confirm skipped by status", confirmRes, {
        buyerEmail: buyer.email,
        orderItemId: item.orderItemId,
      });
      continue;
    }

    check(confirmRes, {
      "confirm status is 200": (r) => r.status === 200,
    });
    {
      logUnexpectedResponse("confirm failed", confirmRes, {
        buyerEmail: buyer.email,
        orderItemId: item.orderItemId,
      });
      fail("confirm failed");
    }
  }
}

function loginOrSkip(user, csrfRef, userType) {
  const loginResult = login(BASE_URL, user.email, user.password, {
    csrf: csrfRef.value,
    failOnError: false,
  });
  const checkName = `${userType} login status is 200`;
  const ok = check(loginResult.res, {
    [checkName]: (r) => r.status === 200,
  });

  if (!ok) {
    logUnexpectedResponse(`${userType} login failed`, loginResult.res, {
      email: user.email,
      id: user.id,
    });
    return false;
  }

  return true;
}

const sellers = new SharedArray("available seller emails", function () {
  return JSON.parse(open("./data/available_seller_email.json")).map((row) => ({
    id: row.id,
    email: row.email,
    password: USER_PASSWORD,
  }));
});

const buyers = new SharedArray("available buyer emails", function () {
  return JSON.parse(open("./data/available_buyer_email.json")).map((row) => ({
    id: row.id,
    email: row.email,
    password: USER_PASSWORD,
  }));
});

export const options = {
  scenarios: {
    process_order_items: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || "2h",
    },
  },
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  for (const seller of sellers) {
    const csrfRef = { value: getCsrf(BASE_URL) };
    if (!loginOrSkip(seller, csrfRef, "seller")) {
      continue;
    }

    while (true) {
      const searchRes = fetchSellerOrderedItems(seller.id);

      if (searchRes.items.length === 0) {
        break;
      }

      transitionSellerItems(seller, csrfRef, searchRes.items);
    }

    logout(BASE_URL, { csrf: csrfRef.value });
  }

  for (const buyer of buyers) {
    const csrfRef = { value: getCsrf(BASE_URL) };
    if (!loginOrSkip(buyer, csrfRef, "buyer")) {
      continue;
    }

    while (true) {
      const deliveredItems = collectDeliveredBuyerItems();

      if (deliveredItems.length === 0) {
        break;
      }

      confirmBuyerItems(buyer, csrfRef, deliveredItems);
    }

    logout(BASE_URL, { csrf: csrfRef.value });
  }
}
