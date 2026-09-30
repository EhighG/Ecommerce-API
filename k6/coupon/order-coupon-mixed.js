import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { Counter } from "k6/metrics";
import { scenario } from "k6/execution";
import { parseOrderCouponCandidateCsv } from "../lib/coupon-candidates.js";
import {
  getCsrf,
  login,
  logUnexpectedResponse,
  patchWithCsrfRetry,
  postJsonWithCsrfRetry,
  readApiError,
} from "../lib/auth.js";
import { env, passwordOrEnv } from "../lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/order-coupon-mixed.js",
  );
}

const ORDER_COUPON_CANDIDATES_FILE =
  __ENV.ORDER_COUPON_CANDIDATES_FILE ||
  "../data/order_coupon_mixed_candidates.csv";
const CANCEL_RATIO = Number(__ENV.CANCEL_RATIO || 0.3);
const ORDER_P95_MS = Number(__ENV.ORDER_P95_MS || 1000);
const ORDER_P99_MS = Number(__ENV.ORDER_P99_MS || 2500);

// Load profiles. Keep one profile active by env vars, or uncomment the
// matching constants below and comment out the env-driven constants.
//
// Smoke:
// const orderRate = 1;
// const duration = "30s";
// const preAllocatedVUs = 5;
// const maxVUs = 20;
//
// Baseline mixed traffic:
// const orderRate = 5;
// const duration = "3m";
// const preAllocatedVUs = 30;
// const maxVUs = 100;
//
// Coupon consistency under moderate order load:
// - Prepare couponIssueId only for a subset of candidate rows.
// - Recommended coupon row ratio: 10-20%.
// const orderRate = 10;
// const duration = "5m";
// const preAllocatedVUs = 50;
// const maxVUs = 200;
//
// Higher order load:
// const orderRate = 20;
// const duration = "5m";
// const preAllocatedVUs = 100;
// const maxVUs = 400;

const orderRate = Number(__ENV.ORDER_RATE || 10);
const duration = __ENV.DURATION || "5m";
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 50);
const maxVUs = Number(__ENV.MAX_VUS || 200);

const orderCouponOrderSuccess = new Counter("order_coupon_order_success");
const orderCouponCancelSuccess = new Counter("order_coupon_cancel_success");
const orderCouponExpectedFailure = new Counter("order_coupon_expected_failure");
const orderCouponUnexpectedFailure = new Counter(
  "order_coupon_unexpected_failure",
);

const EXPECTED_FAILURE_CODES = new Set([
  2501, // INSUFFICIENT_INVENTORY
  3003, // WRONG_STATUS_CHANGE
  7000, // CART_ITEM_NOT_FOUND
  7501, // COUPON_EXPIRED
  7502, // INVALID_COUPON_STATUS
  7509, // DUPLICATED_COUPON
  7510, // COUPON_ISSUED_NOT_FOUND
]);

const candidates = new SharedArray(
  "order coupon mixed candidates",
  function () {
    return parseOrderCouponCandidateCsv(open(ORDER_COUPON_CANDIDATES_FILE));
  },
);

if (candidates.length === 0) {
  throw new Error(
    `${ORDER_COUPON_CANDIDATES_FILE} must contain at least one candidate`,
  );
}

export const options = {
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    order_coupon_mixed: {
      executor: "constant-arrival-rate",
      rate: orderRate,
      timeUnit: "1s",
      duration,
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    checks: ["rate>0.95"],
    order_coupon_unexpected_failure: ["count==0"],
    dropped_iterations: ["count==0"],
    "http_req_duration{name:POST /orders}": [
      `p(95)<${ORDER_P95_MS}`,
      `p(99)<${ORDER_P99_MS}`,
    ],
  },
};

function pickCandidate() {
  const index = scenario.iterationInTest;

  if (index >= candidates.length) {
    fail(
      `not enough order coupon candidates: iteration=${index}, candidates=${candidates.length}. Increase candidate data or reduce ORDER_RATE/DURATION.`,
    );
  }

  return candidates[index];
}

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function buildOrderReq(candidate) {
  const item = {
    cartItemId: candidate.cartItemId,
    orderQuantity: candidate.orderQuantity,
  };

  if (candidate.couponIssueId) {
    item.couponIssuedId = candidate.couponIssueId;
  }

  return { items: [item] };
}

function shouldCancel(candidate) {
  if (typeof candidate.cancelAfterOrder === "boolean") {
    return candidate.cancelAfterOrder;
  }

  if (CANCEL_RATIO <= 0) {
    return false;
  }

  return scenario.iterationInTest % 100 < Math.floor(CANCEL_RATIO * 100);
}

function isExpectedFailure(res) {
  const apiError = readApiError(res);
  return apiError && EXPECTED_FAILURE_CODES.has(apiError.code);
}

function getOrderDetail(orderId) {
  const res = http.get(`${BASE_URL}/orders/${orderId}`, {
    tags: { name: "GET /orders/{id}" },
  });
  return { res, body: parseJson(res) };
}

function findTargetOrderItem(orderDetail, candidate) {
  const items = orderDetail?.itemList;
  if (!Array.isArray(items) || items.length === 0) {
    return null;
  }

  if (candidate.couponIssueId) {
    const couponItem = items.find(
      (item) =>
        Number(item?.usedCoupon?.couponIssuedId) === candidate.couponIssueId,
    );
    if (couponItem) {
      return couponItem;
    }
  }

  return (
    items.find(
      (item) => Number(item?.product?.productId) === candidate.productId,
    ) || items[0]
  );
}

function logCandidateFailure(label, candidate, extra = {}) {
  console.error(
    JSON.stringify({
      label,
      iteration: scenario.iterationInTest,
      email: candidate.email,
      userId: candidate.userId,
      cartItemId: candidate.cartItemId,
      productId: candidate.productId,
      orderQuantity: candidate.orderQuantity,
      couponEventId: candidate.couponEventId,
      couponIssueId: candidate.couponIssueId,
      ...extra,
    }),
  );
}

function recordExpectedFailure(label, candidate, res, requestBody) {
  orderCouponExpectedFailure.add(1);
  const apiError = readApiError(res);
  console.log(
    JSON.stringify({
      label,
      iteration: scenario.iterationInTest,
      email: candidate.email,
      userId: candidate.userId,
      cartItemId: candidate.cartItemId,
      productId: candidate.productId,
      couponEventId: candidate.couponEventId,
      couponIssueId: candidate.couponIssueId,
      status: res.status,
      errorCode: apiError?.code,
      errorMessage: apiError?.message,
      requestBody,
    }),
  );
}

function recordUnexpectedFailure(label, candidate, res, requestBody) {
  orderCouponUnexpectedFailure.add(1);
  logUnexpectedResponse(label, res, {
    email: candidate.email,
    cartItemId: candidate.cartItemId,
    couponIssueId: candidate.couponIssueId,
    iteration: scenario.iterationInTest,
  });
  logCandidateFailure(label, candidate, {
    status: res.status,
    requestBody,
    responseBody: res.body,
  });
  fail(label);
}

export default function () {
  const candidate = pickCandidate();
  const csrfRef = { value: getCsrf(BASE_URL) };

  const loginResult = login(
    BASE_URL,
    candidate.email,
    passwordOrEnv(candidate.password),
    {
      csrf: csrfRef.value,
      failOnError: false,
    },
  );
  const loginOk = check(loginResult.res, {
    "login status is 200": (r) => r.status === 200,
  });
  if (!loginOk) {
    logUnexpectedResponse("login failed", loginResult.res, {
      email: candidate.email,
      iteration: scenario.iterationInTest,
    });
    logCandidateFailure("login failed", candidate, {
      status: loginResult.res.status,
    });
    fail("login failed");
  }

  const orderReq = buildOrderReq(candidate);
  const orderRes = postJsonWithCsrfRetry(
    BASE_URL,
    "/orders",
    orderReq,
    csrfRef,
    { tags: { name: "POST /orders" } },
  );

  if (orderRes.status !== 200) {
    if (isExpectedFailure(orderRes)) {
      recordExpectedFailure(
        "expected order coupon failure",
        candidate,
        orderRes,
        orderReq,
      );
      return;
    }
    recordUnexpectedFailure(
      "unexpected order coupon failure",
      candidate,
      orderRes,
      orderReq,
    );
    return;
  }

  const orderId = Number(orderRes.body);
  const orderOk = check(orderRes, {
    "order coupon create status is 200": (r) => r.status === 200,
    "order coupon create returns order id": () => Number.isInteger(orderId),
  });
  if (!orderOk) {
    recordUnexpectedFailure(
      "order coupon response invalid",
      candidate,
      orderRes,
      orderReq,
    );
    return;
  }

  const detail = getOrderDetail(orderId);
  const targetOrderItem = findTargetOrderItem(detail.body, candidate);
  const detailOk = check(detail.res, {
    "order detail status is 200": (r) => r.status === 200,
    "order detail has target item": () => !!targetOrderItem,
    "coupon order has used coupon": () =>
      !candidate.couponIssueId ||
      Number(targetOrderItem?.usedCoupon?.couponIssuedId) ===
        candidate.couponIssueId,
  });
  if (!detailOk) {
    recordUnexpectedFailure(
      "order coupon detail invalid",
      candidate,
      detail.res,
      orderReq,
    );
    return;
  }

  orderCouponOrderSuccess.add(1);

  if (!shouldCancel(candidate)) {
    return;
  }

  const cancelRes = patchWithCsrfRetry(
    BASE_URL,
    `/order-items/${targetOrderItem.orderItemId}/cancel`,
    csrfRef,
    { tags: { name: "PATCH /order-items/{id}/cancel" } },
  );

  if (cancelRes.status !== 200) {
    if (isExpectedFailure(cancelRes)) {
      recordExpectedFailure(
        "expected order coupon cancel failure",
        candidate,
        cancelRes,
        {
          orderId,
          orderItemId: targetOrderItem.orderItemId,
        },
      );
      return;
    }
    recordUnexpectedFailure(
      "unexpected order coupon cancel failure",
      candidate,
      cancelRes,
      {
        orderId,
        orderItemId: targetOrderItem.orderItemId,
      },
    );
    return;
  }

  const cancelOk = check(cancelRes, {
    "order coupon cancel status is 200": (r) => r.status === 200,
  });
  if (!cancelOk) {
    recordUnexpectedFailure(
      "order coupon cancel response invalid",
      candidate,
      cancelRes,
      {
        orderId,
        orderItemId: targetOrderItem.orderItemId,
      },
    );
    return;
  }

  const afterCancel = getOrderDetail(orderId);
  const canceledOrderItem = findTargetOrderItem(afterCancel.body, candidate);
  const afterCancelOk = check(afterCancel.res, {
    "order detail after cancel status is 200": (r) => r.status === 200,
    "order item status is canceled": () =>
      canceledOrderItem?.status === "CANCELED",
  });
  if (!afterCancelOk) {
    recordUnexpectedFailure(
      "order coupon cancel detail invalid",
      candidate,
      afterCancel.res,
      {
        orderId,
        orderItemId: targetOrderItem.orderItemId,
      },
    );
    return;
  }

  orderCouponCancelSuccess.add(1);
}
