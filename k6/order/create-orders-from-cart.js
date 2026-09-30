import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import {
  getCsrf,
  login,
  logUnexpectedResponse,
  withCsrfHeaders,
} from "../lib/auth.js";
import { env } from "../lib/env.js";

const BASE_URL = env("BASE_URL");
const BUYER_PASSWORD = env("PASSWORD");
const ORDER_CART_ITEM_COUNT = 10;

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

function cartItemToExpectation(item) {
  return {
    cartItemId: item.id,
    productId: item.product?.id,
    quantity: item.quantity,
  };
}

function orderItemsMatchCartItems(orderItems, expectedItems) {
  if (
    !Array.isArray(orderItems) ||
    orderItems.length !== expectedItems.length
  ) {
    return false;
  }

  const expectedByProductId = new Map();
  for (const item of expectedItems) {
    expectedByProductId.set(item.productId, item.quantity);
  }

  for (const orderItem of orderItems) {
    const productId = orderItem.product?.productId;
    const expectedQuantity = expectedByProductId.get(productId);

    if (
      expectedQuantity === undefined ||
      orderItem.quantity !== expectedQuantity
    ) {
      return false;
    }
  }

  return true;
}

const buyers = new SharedArray("available buyer emails", function () {
  return JSON.parse(open("./data/available_buyer_email.json")).map((row) => ({
    email: row.email,
    password: BUYER_PASSWORD,
  }));
});

const vus = Number(__ENV.VUS || 10);
const iterations = Number(__ENV.ORDER_ITERATIONS || buyers.length);
// const iterations = Number(3);
const maxDuration = __ENV.MAX_DURATION || "10m";

export const options = {
  scenarios: {
    create_orders_from_cart: {
      executor: "shared-iterations",
      vus,
      iterations,
      maxDuration,
    },
  },
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const buyer = buyers[scenario.iterationInTest % buyers.length];

  login(BASE_URL, buyer.email, buyer.password);

  const cartRes = http.get(`${BASE_URL}/cart-items`, {
    tags: { name: "GET /cart-items" },
  });
  const cartItems = parseJson(cartRes);
  const cartReady = check(cartRes, {
    "cart item list status is 200": (r) => r.status === 200,
    "cart has at least 10 items": () =>
      Array.isArray(cartItems) && cartItems.length >= ORDER_CART_ITEM_COUNT,
  });

  if (!cartReady) {
    logUnexpectedResponse("cart item list failed", cartRes, {
      buyerEmail: buyer.email,
    });
    fail("cart item list failed");
  }

  const selectedCartItems = sampleWithoutReplacement(
    cartItems,
    ORDER_CART_ITEM_COUNT,
  );
  const expectedItems = selectedCartItems.map(cartItemToExpectation);
  const orderReq = {
    items: expectedItems.map((item) => ({
      cartItemId: item.cartItemId,
      orderQuantity: item.quantity,
    })),
  };

  const csrf = getCsrf(BASE_URL);
  const orderRes = http.post(`${BASE_URL}/orders`, JSON.stringify(orderReq), {
    headers: withCsrfHeaders(csrf, {
      "Content-Type": "application/json",
    }),
    tags: { name: "POST /orders" },
  });
  const orderId = Number(orderRes.body);
  const orderCreated = check(orderRes, {
    "create order status is 200": (r) => r.status === 200,
    "create order returns order id": (r) =>
      r.body.length > 0 && Number.isInteger(Number(r.body)),
  });

  if (!orderCreated) {
    logUnexpectedResponse("create order failed", orderRes, {
      buyerEmail: buyer.email,
    });
    fail("create order failed");
  }

  const myOrdersRes = http.get(`${BASE_URL}/orders/me`, {
    tags: { name: "GET /orders/me" },
  });
  const myOrders = parseJson(myOrdersRes);
  const orderFound = check(myOrdersRes, {
    "my order list status is 200": (r) => r.status === 200,
    "my order list contains created order": () =>
      Array.isArray(myOrders) &&
      myOrders.some((order) => order.orderId === orderId),
  });

  if (!orderFound) {
    logUnexpectedResponse("created order not found in my orders", myOrdersRes, {
      buyerEmail: buyer.email,
      orderId,
    });
    fail("created order not found in my orders");
  }

  const orderItemsRes = http.get(`${BASE_URL}/order-items?orderId=${orderId}`, {
    tags: { name: "GET /order-items" },
  });
  const orderItems = parseJson(orderItemsRes);
  const orderItemsMatched = check(orderItemsRes, {
    "order item list status is 200": (r) => r.status === 200,
    "order items match selected cart items": () =>
      orderItemsMatchCartItems(orderItems, expectedItems),
  });

  if (!orderItemsMatched) {
    logUnexpectedResponse(
      "order items did not match selected cart items",
      orderItemsRes,
      {
        buyerEmail: buyer.email,
        orderId,
      },
    );
    fail("order items did not match selected cart items");
  }
}
