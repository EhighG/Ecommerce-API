import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import {
  getCsrf,
  login,
  logUnexpectedResponse,
  postJsonWithCsrfRetry,
} from "./lib/auth.js";
import { env } from "./lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/buyer-purchase-flow.js",
  );
}

const BUYER_PASSWORD = env("PASSWORD");
const PRODUCT_SEARCH_SIZE = Number(__ENV.PRODUCT_SEARCH_SIZE || 50);
const PRODUCT_PICK_COUNT = Number(__ENV.PRODUCT_PICK_COUNT || 3);
const MAX_QUANTITY = Number(__ENV.MAX_QUANTITY || 3);
const VERIFY_ORDER = __ENV.VERIFY_ORDER !== "false";

const purchaseFlowRate = Number(
  __ENV.PURCHASE_FLOW_RATE || __ENV.FLOW_RATE || 10,
);
const duration = __ENV.DURATION || "2m";
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 30);
const maxVUs = Number(__ENV.MAX_VUS || 200);

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function sampleWithoutReplacement(items, count) {
  const pool = items.slice();
  const picked = [];
  const limit = Math.min(count, pool.length);

  for (let i = 0; i < limit; i += 1) {
    const index = randomInt(0, pool.length - 1);
    picked.push(pool[index]);
    pool.splice(index, 1);
  }

  return picked;
}

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function jsonLogValue(value) {
  return JSON.stringify(value);
}

function logValidationFailure(label, context = {}) {
  const suffix = Object.entries(context)
    .map(([key, value]) => `${key}=${value}`)
    .join(", ");

  console.error(`${label}${suffix ? `: ${suffix}` : ""}`);
}

function validPageSizeFor(count) {
  if (count <= 20) return 20;
  if (count <= 50) return 50;
  return 100;
}

function pageItemCount(totalElements, page, size) {
  if (typeof totalElements !== "number") return 0;
  return Math.max(0, Math.min(size, totalElements - size * page));
}

function cartItemMatchesCartItemIds(cartItem, cartItemIds) {
  return cartItemIds.has(cartItem.id);
}

function buildExpectedItems(cartItems, addedCartItemIds) {
  return cartItems
    .filter((item) => cartItemMatchesCartItemIds(item, addedCartItemIds))
    .map((item) => ({
      cartItemId: item.id,
      productId: item.product.id,
      quantity: item.quantity,
    }));
}

function orderItemsMatchExpected(searchResBody, expectedItems) {
  const orderItems = searchResBody?.items;

  if (
    !Array.isArray(orderItems) ||
    orderItems.length !== expectedItems.length
  ) {
    return false;
  }

  const expectedByProductId = new Map();
  for (const item of expectedItems) {
    expectedByProductId.set(item.productId, true);
  }

  for (const orderItem of orderItems) {
    const productId = orderItem.product?.id;
    if (!expectedByProductId.has(productId)) return false;
  }

  return true;
}

const buyers = new SharedArray("available buyer emails", function () {
  return JSON.parse(open("./data/available_buyer_email.json")).map((row) => ({
    email: row.email,
    password: BUYER_PASSWORD,
  }));
});

const productKeywords = new SharedArray("product search keywords", function () {
  return JSON.parse(open("./data/product_name_keywords.json"))
    .map((row) => row.keyword)
    .filter((keyword) => typeof keyword === "string" && keyword.length > 0);
});

if (productKeywords.length === 0) {
  throw new Error(
    "product_name_keywords.json must contain at least one keyword value",
  );
}

export const options = {
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    buyer_purchase_flow: {
      executor: "constant-arrival-rate",
      rate: purchaseFlowRate,
      timeUnit: "1s",
      duration,
      preAllocatedVUs,
      maxVUs,
    },
    // // Quick validation scenario. Enable this before sustained load tests
    // // when you only want to verify that the purchase flow works end to end.
    // buyer_purchase_flow_once: {
    //   executor: "shared-iterations",
    //   vus: Number(__ENV.VUS || 3),
    //   iterations: Number(__ENV.FLOW_ITERATIONS || 3),
    //   maxDuration: __ENV.MAX_DURATION || "2m",
    // },
  },
  thresholds: {
    checks: ["rate>0.999"],
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500", "p(99)<1500"],
    iteration_duration: ["p(95)<3000"],
    dropped_iterations: ["count==0"],
    "http_req_duration{name:GET /products}": ["p(95)<500"],
    "http_req_duration{name:POST /cart-items}": ["p(95)<700"],
    "http_req_duration{name:POST /orders}": ["p(95)<1000"],
  },
};

function buildProductSearchUrl(keyword, page) {
  const query = [
    `keyword=${encodeURIComponent(keyword)}`,
    `page=${page}`,
    `size=${PRODUCT_SEARCH_SIZE}`,
    "sortBy=ORDER_COUNT",
    "direction=DESC",
  ].join("&");

  return `${BASE_URL}/products?${query}`;
}

export default function () {
  const buyer = buyers[scenario.iterationInTest % buyers.length];
  const keyword =
    productKeywords[scenario.iterationInTest % productKeywords.length];
  const csrfRef = { value: getCsrf(BASE_URL) };

  login(BASE_URL, buyer.email, buyer.password, { csrf: csrfRef.value });

  const firstSearchPage = 0;
  let searchUrl = buildProductSearchUrl(keyword, firstSearchPage);
  let searchRes = http.get(searchUrl, {
    tags: { name: "GET /products" },
  });
  let searchBody = parseJson(searchRes);
  let searchOk = check(searchRes, {
    "product search status is 200": (r) => r.status === 200,
    "product search returns items": () =>
      Array.isArray(searchBody?.items) && searchBody.items.length > 0,
  });

  if (!searchOk) {
    logUnexpectedResponse("product search failed", searchRes, {
      buyerEmail: buyer.email,
      url: searchUrl,
      keyword,
      page: firstSearchPage,
      size: PRODUCT_SEARCH_SIZE,
      sortBy: "ORDER_COUNT",
      direction: "DESC",
    });
    fail("product search failed");
  }

  const secondSearchPage = 1;
  const secondPageItemCount = pageItemCount(
    searchBody.totalElements,
    secondSearchPage,
    PRODUCT_SEARCH_SIZE,
  );
  if (
    (scenario.iterationInTest + 1) % 10 === 0 &&
    searchBody.totalPages > secondSearchPage &&
    secondPageItemCount >= PRODUCT_PICK_COUNT
  ) {
    searchUrl = buildProductSearchUrl(keyword, secondSearchPage);
    searchRes = http.get(searchUrl, {
      tags: { name: "GET /products" },
    });
    searchBody = parseJson(searchRes);
    searchOk = check(searchRes, {
      "product search page 2 status is 200": (r) => r.status === 200,
      "product search page 2 returns items": () =>
        Array.isArray(searchBody?.items) && searchBody.items.length > 0,
    });

    if (!searchOk) {
      logUnexpectedResponse("product search page 2 failed", searchRes, {
        buyerEmail: buyer.email,
        url: searchUrl,
        keyword,
        page: secondSearchPage,
        size: PRODUCT_SEARCH_SIZE,
        sortBy: "ORDER_COUNT",
        direction: "DESC",
      });
      fail("product search page 2 failed");
    }
  }

  const purchasableProducts = searchBody.items.filter(
    (product) => product.id && product.inventoryQuantity > 0,
  );
  const enoughProducts = check(searchRes, {
    "enough purchasable products": () =>
      purchasableProducts.length >= PRODUCT_PICK_COUNT,
  });

  if (!enoughProducts) {
    logValidationFailure("not enough purchasable products", {
      buyerEmail: buyer.email,
      url: searchUrl,
      keyword,
      productSearchSize: PRODUCT_SEARCH_SIZE,
      productPickCount: PRODUCT_PICK_COUNT,
      maxQuantity: MAX_QUANTITY,
      returnedItems: searchBody.items.length,
      availablePurchasableProducts: purchasableProducts.length,
      requiredPurchasableProducts: PRODUCT_PICK_COUNT,
      productIds: jsonLogValue(searchBody.items.map((product) => product.id)),
      purchasableProductIds: jsonLogValue(
        purchasableProducts.map((product) => product.id),
      ),
    });
    fail("not enough purchasable products");
  }

  const selectedProducts = sampleWithoutReplacement(
    purchasableProducts,
    PRODUCT_PICK_COUNT,
  );
  const addedCartItems = [];

  for (const product of selectedProducts) {
    const quantity = randomInt(
      1,
      Math.min(MAX_QUANTITY, product.inventoryQuantity),
    );
    const addCartItemRequest = { productId: product.id, quantity };
    const addRes = postJsonWithCsrfRetry(
      BASE_URL,
      "/cart-items",
      addCartItemRequest,
      csrfRef,
      { tags: { name: "POST /cart-items" } },
    );
    const cartItemId = Number(addRes.body);

    const addOk = check(addRes, {
      "add cart item status is 200": (r) => r.status === 200,
      "add cart item returns cart item id": () => Number.isInteger(cartItemId),
    });

    if (!addOk) {
      logUnexpectedResponse("add cart item failed", addRes, {
        buyerEmail: buyer.email,
        path: "/cart-items",
        requestBody: jsonLogValue(addCartItemRequest),
        productId: product.id,
        quantity,
        productInventoryQuantity: product.inventoryQuantity,
      });
      fail("add cart item failed");
    }

    addedCartItems.push({
      cartItemId,
      productId: product.id,
    });
  }

  const cartAfterRes = http.get(`${BASE_URL}/cart-items`, {
    tags: { name: "GET /cart-items" },
  });
  const cartAfter = parseJson(cartAfterRes);
  const addedCartItemIds = new Set(
    addedCartItems.map((item) => item.cartItemId),
  );
  const expectedItems = Array.isArray(cartAfter)
    ? buildExpectedItems(cartAfter, addedCartItemIds)
    : [];
  const cartAfterOk = check(cartAfterRes, {
    "cart after add status is 200": (r) => r.status === 200,
    "cart after add contains added cart items": () =>
      expectedItems.length === PRODUCT_PICK_COUNT,
  });

  if (!cartAfterOk) {
    logUnexpectedResponse("cart after add failed", cartAfterRes, {
      buyerEmail: buyer.email,
      url: `${BASE_URL}/cart-items`,
      productPickCount: PRODUCT_PICK_COUNT,
      addedCartItems: jsonLogValue(addedCartItems),
      expectedCartItemIds: jsonLogValue([...addedCartItemIds]),
      expectedItems: jsonLogValue(expectedItems),
    });
    fail("cart after add failed");
  }

  const createOrderRequest = {
    items: expectedItems.map((item) => ({
      cartItemId: item.cartItemId,
      orderQuantity: item.quantity,
    })),
  };
  const orderRes = postJsonWithCsrfRetry(
    BASE_URL,
    "/orders",
    createOrderRequest,
    csrfRef,
    { tags: { name: "POST /orders" } },
  );
  const orderId = Number(orderRes.body);
  const orderOk = check(orderRes, {
    "create order status is 200": (r) => r.status === 200,
    "create order returns order id": (r) =>
      r.body.length > 0 && Number.isInteger(Number(r.body)),
  });

  if (!orderOk) {
    logUnexpectedResponse("create order failed", orderRes, {
      buyerEmail: buyer.email,
      path: "/orders",
      requestBody: jsonLogValue(createOrderRequest),
      expectedItems: jsonLogValue(expectedItems),
    });
    fail("create order failed");
  }

  if (VERIFY_ORDER) {
    const myOrdersRes = http.get(`${BASE_URL}/orders/me`, {
      tags: { name: "GET /orders/me" },
    });
    const myOrders = parseJson(myOrdersRes);
    const myOrdersOk = check(myOrdersRes, {
      "my order list status is 200": (r) => r.status === 200,
      "my order list contains created order": () =>
        Array.isArray(myOrders) &&
        myOrders.some((order) => order.orderId === orderId),
    });

    if (!myOrdersOk) {
      logUnexpectedResponse(
        "created order not found in my orders",
        myOrdersRes,
        {
          buyerEmail: buyer.email,
          url: `${BASE_URL}/orders/me`,
          orderId,
          createOrderRequest: jsonLogValue(createOrderRequest),
          expectedItems: jsonLogValue(expectedItems),
        },
      );
      fail("created order not found in my orders");
    }

    const orderItemSearchSize = validPageSizeFor(PRODUCT_PICK_COUNT);
    const orderItemsUrl = `${BASE_URL}/order-items?orderId=${orderId}&size=${orderItemSearchSize}`;
    const orderItemsRes = http.get(
      orderItemsUrl,
      {
        tags: { name: "GET /order-items" },
      },
    );
    const orderItemsBody = parseJson(orderItemsRes);
    const orderItemsOk = check(orderItemsRes, {
      "order item search status is 200": (r) => r.status === 200,
      "order items contain ordered products": () =>
        orderItemsMatchExpected(orderItemsBody, expectedItems),
    });

    if (!orderItemsOk) {
      logUnexpectedResponse(
        "order items did not match cart items",
        orderItemsRes,
        {
          buyerEmail: buyer.email,
          url: orderItemsUrl,
          orderId,
          size: orderItemSearchSize,
          expectedItems: jsonLogValue(expectedItems),
        },
      );
      fail("order items did not match cart items");
    }
  }
}
