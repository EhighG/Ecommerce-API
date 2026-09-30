import http from "k6/http";
import { check } from "k6";
import { SharedArray } from "k6/data";
import {
  getCsrf,
  login,
  logout,
  logUnexpectedResponse,
  postJsonWithCsrfRetry,
} from "./lib/auth.js";
import { env } from "./lib/env.js";

const BASE_URL = env("BASE_URL");
const USER_PASSWORD = env("PASSWORD");
const PAGE_SIZE = 100;

let reviewAttemptCount = 0;

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function nextHalfStars() {
  reviewAttemptCount += 1;

  if (reviewAttemptCount % 10 === 0) {
    return randomInt(1, 10);
  }

  return randomInt(7, 10);
}

function buildReviewContent(item, buyerEmail, halfStars) {
  return `${item.product.name} ${String(halfStars)} ${buyerEmail}`.slice(0, 255);
}

function fetchBuyerOrders(buyerEmail) {
  const res = http.get(`${BASE_URL}/orders/me`, {
    tags: { name: "GET /orders/me" },
  });
  const body = parseJson(res);
  const ok = check(res, {
    "buyer order list status is 200": (r) => r.status === 200,
    "buyer order list is array": () => Array.isArray(body),
  });

  if (!ok) {
    logUnexpectedResponse("buyer order list failed", res, { buyerEmail });
    return null;
  }

  return body;
}

function fetchBuyerOrderItems(orderId, buyerEmail, page) {
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
      buyerEmail,
      orderId,
      page,
    });
    return null;
  }

  return body;
}

function collectReviewableItems(buyerEmail) {
  const orders = fetchBuyerOrders(buyerEmail);
  if (!orders) {
    return null;
  }

  const itemsByProductId = new Map();

  for (const order of orders) {
    let page = 0;

    while (true) {
      const searchRes = fetchBuyerOrderItems(order.orderId, buyerEmail, page);
      if (!searchRes) {
        return null;
      }

      for (const item of searchRes.items) {
        if (item.status !== "PURCHASE_CONFIRMED") {
          continue;
        }

        const productId = item.product?.id;
        if (!productId || itemsByProductId.has(productId)) {
          continue;
        }

        itemsByProductId.set(productId, item);
      }

      if (!searchRes.hasNext) {
        break;
      }

      page += 1;
    }
  }

  return Array.from(itemsByProductId.values());
}

function loginOrSkip(buyer, csrfRef) {
  const loginResult = login(BASE_URL, buyer.email, buyer.password, {
    csrf: csrfRef.value,
    failOnError: false,
  });
  const ok = check(loginResult.res, {
    "review buyer login status is 200": (r) => r.status === 200,
  });

  if (!ok) {
    logUnexpectedResponse("review buyer login failed", loginResult.res, {
      email: buyer.email,
      id: buyer.id,
    });
    return false;
  }

  return true;
}

const buyers = new SharedArray("reviewable buyer emails", function () {
  return JSON.parse(open("./data/reviewable_buyer_email.json")).map((row) => ({
    id: row.id,
    email: row.email,
    password: USER_PASSWORD,
  }));
});

export const options = {
  scenarios: {
    write_reviews: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || "2h",
    },
  },
};

export default function () {
  for (const buyer of buyers) {
    const csrfRef = { value: getCsrf(BASE_URL) };

    if (!loginOrSkip(buyer, csrfRef)) {
      continue;
    }

    const reviewableItems = collectReviewableItems(buyer.email);
    if (!reviewableItems) {
      logout(BASE_URL, { csrfRef, failOnError: false });
      continue;
    }

    let shouldContinueNextBuyer = false;

    for (const item of reviewableItems) {
      const halfStars = nextHalfStars();
      const reviewRes = postJsonWithCsrfRetry(
        BASE_URL,
        `/products/${item.product.id}/reviews`,
        {
          halfStars,
          content: buildReviewContent(item, buyer.email, halfStars),
        },
        csrfRef,
        { tags: { name: "POST /products/{productId}/reviews" } },
      );

      if (reviewRes.status === 200) {
        check(reviewRes, {
          "write review status is 200": (r) => r.status === 200,
        });
        continue;
      }

      if (reviewRes.status === 403) {
        logUnexpectedResponse("write review forbidden", reviewRes, {
          buyerEmail: buyer.email,
          productId: item.product.id,
        });
        shouldContinueNextBuyer = true;
        break;
      }

      logUnexpectedResponse("write review failed", reviewRes, {
        buyerEmail: buyer.email,
        productId: item.product.id,
      });
      shouldContinueNextBuyer = true;
      break;
    }

    logout(BASE_URL, { csrfRef, failOnError: false });

    if (shouldContinueNextBuyer) {
      continue;
    }
  }
}
