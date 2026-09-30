import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import { parseCandidateCsv } from "../lib/candidates.js";
import {
  getCsrf,
  login,
  logUnexpectedResponse,
  postJsonWithCsrfRetry,
} from "../lib/auth.js";
import { env, passwordOrEnv } from "../lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/order-create-realistic.js",
  );
}

const ORDER_CANDIDATES_FILE =
  __ENV.ORDER_CANDIDATES_FILE || "./data/order_create_candidates.csv";

// Load profiles. Keep one profile active by env vars, or uncomment the
// matching constants below and comment out the env-driven constants.
//
// Smoke:
// const orderRate = 1;
// const duration = "30s";
// const preAllocatedVUs = 5;
// const maxVUs = 20;
//
// // Baseline:
// const orderRate = 3;
// const duration = "1m";
// const preAllocatedVUs = 30;
// const maxVUs = 100;
//
// // Moderate:
// const orderRate = 5;
// const duration = "5m";
// const preAllocatedVUs = 30;
// const maxVUs = 100;
//
// // Stress:
// const orderRate = 10;
// const duration = "3m";
// const preAllocatedVUs = 30;
// const maxVUs = 150;
//
// Limit search with current 1000-candidate file:
// const orderRate = 15;
// const duration = "5m";
// const preAllocatedVUs = 150;
// const maxVUs = 500;
//
// 부하 up(26.04.30 22:32)
const orderRate = 20;
const duration = "3m";
const preAllocatedVUs = 50;
const maxVUs = 250;
//
// Use ENV variables
// const orderRate = Number(__ENV.ORDER_RATE || 5);
// const duration = __ENV.DURATION || "1m";
// const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 50);
// const maxVUs = Number(__ENV.MAX_VUS || 200);

const p95Ms = Number(__ENV.ORDER_P95_MS || 1000);
const p99Ms = Number(__ENV.ORDER_P99_MS || 2500);

const orderCandidates = new SharedArray("order create candidates", function () {
  return parseCandidateCsv(open(ORDER_CANDIDATES_FILE));
});

if (orderCandidates.length === 0) {
  throw new Error(
    `${ORDER_CANDIDATES_FILE} must contain at least one candidate`,
  );
}

export const options = {
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    order_create_realistic: {
      executor: "constant-arrival-rate",
      rate: orderRate,
      timeUnit: "1s",
      duration,
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    checks: ["rate>0.999"],
    http_req_failed: ["rate<0.01"],
    dropped_iterations: ["count==0"],
    "http_req_duration{name:POST /orders}": [
      `p(95)<${p95Ms}`,
      `p(99)<${p99Ms}`,
    ],
  },
};

function pickCandidate() {
  const index = scenario.iterationInTest;

  if (index >= orderCandidates.length) {
    fail(
      `not enough order candidates: iteration=${index}, candidates=${orderCandidates.length}. Increase candidate data or reduce ORDER_RATE/DURATION.`,
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
  const orderRes = postJsonWithCsrfRetry(
    BASE_URL,
    "/orders",
    orderReq,
    csrfRef,
    { tags: { name: "POST /orders" } },
  );
  const orderId = Number(orderRes.body);
  const ok = check(orderRes, {
    "create order status is 200": (r) => r.status === 200,
    "create order returns order id": () => Number.isInteger(orderId),
  });

  if (!ok) {
    logUnexpectedResponse("create order failed", orderRes, {
      email: candidate.email,
      candidateIndex: scenario.iterationInTest,
    });
    logCandidateFailure(
      "create order failed",
      candidate,
      scenario.iterationInTest,
      {
        status: orderRes.status,
        requestBody: orderReq,
        responseBody: orderRes.body,
      },
    );
    fail("create order failed");
  }
}
