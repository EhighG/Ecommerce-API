import { check, fail } from "k6";
import { Counter } from "k6/metrics";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import { parseCandidateCsv } from "../lib/candidates.js";
import {
  getCsrf,
  login,
  logUnexpectedResponse,
  postJsonWithCsrfRetry,
  readApiError,
} from "../lib/auth.js";
import { env, passwordOrEnv } from "../lib/env.js";
import { newIdempotencyKey } from "../lib/idempotency.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/order/order-stock-concurrency.js",
  );
}

const ORDER_CANDIDATES_FILE =
  __ENV.ORDER_CANDIDATES_FILE ||
  "../data/order_stock_concurrency_candidates.csv";

// Concurrency profiles. Keep one profile active by env vars, or uncomment the
// matching constants below and comment out the env-driven constants.
//
// Smoke:
// const vus = 5;
// const iterations = 10;
// const maxDuration = "1m";
//
// Baseline contention:
// const vus = 30;
// const iterations = 100;
// const maxDuration = "2m";
//
// Target stock correctness with current 300-candidate file:
// - Set target product inventory to 100 before running.
// - Expected: 100 success, 200 insufficient-inventory rejections.
// const vus = 100;
// const iterations = 300;
// const maxDuration = "3m";
//
// Higher contention, requires more candidate rows:
// const vus = 200;
// const iterations = 600;
// const maxDuration = "5m";

const vus = Number(__ENV.VUS || 100);
const iterations = Number(__ENV.ITERATIONS || 300);
const maxDuration = __ENV.MAX_DURATION || "3m";

const orderSuccess = new Counter("order_success");
const orderRejected = new Counter("order_rejected");
const orderUnexpectedFailure = new Counter("order_unexpected_failure");

const orderCandidates = new SharedArray(
  "stock concurrency candidates",
  function () {
    return parseCandidateCsv(open(ORDER_CANDIDATES_FILE));
  },
);

if (orderCandidates.length === 0) {
  throw new Error(
    `${ORDER_CANDIDATES_FILE} must contain at least one candidate`,
  );
}

export const options = {
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    order_stock_concurrency: {
      executor: "shared-iterations",
      vus,
      iterations,
      maxDuration,
    },
  },
  thresholds: {
    checks: ["rate>0.95"],
    order_unexpected_failure: ["count==0"],
  },
};

function pickCandidate() {
  const index = scenario.iterationInTest;

  if (index >= orderCandidates.length) {
    fail(
      `not enough order candidates: iteration=${index}, candidates=${orderCandidates.length}`,
    );
  }

  return orderCandidates[index];
}

function buildOrderReq(candidate) {
  return {
    items: candidate.items.map((item) => ({
      cartItemId: item.cartItemId,
      orderQuantity: item.orderQuantity,
    })),
  };
}

function logCandidateFailure(label, candidate, candidateIndex, extra = {}) {
  console.error(
    JSON.stringify({
      label,
      candidateIndex,
      email: candidate.email,
      items: candidate.items,
      ...extra,
    }),
  );
}

function isExpectedStockRejection(res) {
  if (res.status !== 400) {
    return false;
  }

  const apiError = readApiError(res);

  return apiError?.code === "2501"; // INSUFFICIENT_INVENTORY. 서버 오류 code는 문자열이다
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
      candidateIndex: scenario.iterationInTest,
    });
    logCandidateFailure("login failed", candidate, scenario.iterationInTest, {
      status: loginResult.res.status,
    });
    fail("login failed");
  }

  const orderReq = buildOrderReq(candidate);
  // CSRF 재시도가 일어나도 같은 주문이므로 같은 키를 쓴다
  const orderRes = postJsonWithCsrfRetry(
    BASE_URL,
    "/orders",
    orderReq,
    csrfRef,
    {
      headers: { "Idempotency-Key": newIdempotencyKey("stock") },
      tags: { name: "POST /orders" },
    },
  );

  if (orderRes.status === 200) {
    orderSuccess.add(1);
    const successOk = check(orderRes, {
      "order success returns order id": (r) => Number.isInteger(Number(r.body)),
    });
    if (!successOk) {
      logCandidateFailure(
        "order success response invalid",
        candidate,
        scenario.iterationInTest,
        {
          status: orderRes.status,
          requestBody: orderReq,
          responseBody: orderRes.body,
        },
      );
      fail("order success response invalid");
    }
    return;
  }

  if (isExpectedStockRejection(orderRes)) {
    orderRejected.add(1);
    check(orderRes, {
      "stock rejection status is 400": (r) => r.status === 400,
    });
    return;
  }

  orderUnexpectedFailure.add(1);
  logUnexpectedResponse("unexpected order failure", orderRes, {
    email: candidate.email,
    candidateIndex: scenario.iterationInTest,
  });
  logCandidateFailure(
    "unexpected order failure",
    candidate,
    scenario.iterationInTest,
    {
      status: orderRes.status,
      requestBody: orderReq,
      responseBody: orderRes.body,
    },
  );
  fail("unexpected order failure");
}
