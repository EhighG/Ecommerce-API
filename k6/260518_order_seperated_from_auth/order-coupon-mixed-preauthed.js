import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { Counter } from "k6/metrics";
import { scenario } from "k6/execution";
import { parseOrderCouponCandidateCsv } from "../lib/coupon-candidates.js";
import {
  logUnexpectedResponse,
  readApiError,
  withCsrfHeaders,
} from "../lib/auth.js";
import { env } from "../lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/260518_order_seperated_from_auth/order-coupon-mixed-preauthed.js",
  );
}

const ORDER_COUPON_CANDIDATES_FILE =
  __ENV.ORDER_COUPON_CANDIDATES_FILE ||
  "../data/order_coupon_mixed_candidates.csv";
const PREAUTH_SESSIONS_FILE =
  __ENV.PREAUTH_SESSIONS_FILE ||
  "../260518_order_seperated_from_auth/order-coupon-preauth-sessions.json";
const CANCEL_RATIO = Number(__ENV.CANCEL_RATIO || 0.3);
const ORDER_P95_MS = Number(__ENV.ORDER_P95_MS || 1000);
const ORDER_P99_MS = Number(__ENV.ORDER_P99_MS || 2500);

const orderRate = Number(__ENV.ORDER_RATE || 30);
const duration = __ENV.DURATION || "2m";
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 50);
const maxVUs = Number(__ENV.MAX_VUS || 200);

const orderCouponOrderSuccess = new Counter("order_coupon_order_success");
const orderCouponCancelSuccess = new Counter("order_coupon_cancel_success");
const orderCouponExpectedFailure = new Counter("order_coupon_expected_failure");
const orderCouponUnexpectedFailure = new Counter(
  "order_coupon_unexpected_failure",
);
const orderCouponMissingSession = new Counter("order_coupon_missing_session");

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

const preauthSessions = new SharedArray(
  "order coupon preauth sessions",
  function () {
    const parsed = JSON.parse(open(PREAUTH_SESSIONS_FILE));

    if (!parsed.sessions || typeof parsed.sessions !== "object") {
      throw new Error(`${PREAUTH_SESSIONS_FILE} missing sessions object`);
    }

    return [parsed.sessions];
  },
)[0];

if (candidates.length === 0) {
  throw new Error(
    `${ORDER_COUPON_CANDIDATES_FILE} must contain at least one candidate`,
  );
}

export const options = {
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    order_coupon_mixed_preauthed: {
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
    order_coupon_missing_session: ["count==0"],
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

function sessionHeaders(session, headers = {}) {
  return withCsrfHeaders(session.csrf, {
    Cookie: session.cookieHeader,
    ...headers,
  });
}

function getCsrfForSession(session) {
  const res = http.get(`${BASE_URL}/auth/csrf`, {
    headers: {
      Cookie: session.cookieHeader,
    },
    tags: { name: "GET /auth/csrf" },
  });
  const csrf = parseJson(res);
  const ok = check(res, {
    "session csrf refresh status is 200": (r) => r.status === 200,
    "session csrf refresh headerName exists": () =>
      typeof csrf?.headerName === "string" && csrf.headerName.length > 0,
    "session csrf refresh token exists": () =>
      typeof csrf?.token === "string" && csrf.token.length > 0,
  });

  if (!ok) {
    logUnexpectedResponse("session csrf refresh failed", res);
    fail("session csrf refresh failed");
  }

  return csrf;
}

function requestWithSessionCsrfRetry(
  method,
  path,
  body,
  session,
  options = {},
) {
  const hasJsonBody = body !== null && body !== undefined;
  const requestBody = hasJsonBody ? JSON.stringify(body) : null;
  const params = {
    headers: sessionHeaders(session, {
      ...(hasJsonBody ? { "Content-Type": "application/json" } : {}),
      ...(options.headers || {}),
    }),
    tags: options.tags,
  };

  let res = http.request(method, `${BASE_URL}${path}`, requestBody, params);

  if (res.status !== 403) {
    return res;
  }

  session.csrf = getCsrfForSession(session);

  return http.request(method, `${BASE_URL}${path}`, requestBody, {
    ...params,
    headers: sessionHeaders(session, {
      ...(hasJsonBody ? { "Content-Type": "application/json" } : {}),
      ...(options.headers || {}),
    }),
  });
}

function postJsonWithSessionCsrfRetry(path, body, session, options = {}) {
  return requestWithSessionCsrfRetry("POST", path, body, session, options);
}

function patchWithSessionCsrfRetry(path, session, options = {}) {
  return requestWithSessionCsrfRetry("PATCH", path, null, session, options);
}

function getOrderDetail(orderId, session) {
  const res = http.get(`${BASE_URL}/orders/${orderId}`, {
    headers: {
      Cookie: session.cookieHeader,
    },
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

function getSession(candidate) {
  const session = preauthSessions[candidate.email];

  if (!session) {
    orderCouponMissingSession.add(1);
    fail(`missing preauth session: email=${candidate.email}`);
  }

  return {
    email: session.email,
    userId: session.userId,
    cookieHeader: session.cookieHeader,
    csrf: {
      headerName: session.csrf.headerName,
      token: session.csrf.token,
    },
  };
}

export default function () {
  const candidate = pickCandidate();
  const session = getSession(candidate);

  const orderReq = buildOrderReq(candidate);
  const orderRes = postJsonWithSessionCsrfRetry("/orders", orderReq, session, {
    tags: { name: "POST /orders" },
  });

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

  const detail = getOrderDetail(orderId, session);
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

  const cancelRes = patchWithSessionCsrfRetry(
    `/order-items/${targetOrderItem.orderItemId}/cancel`,
    session,
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

  const afterCancel = getOrderDetail(orderId, session);
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
