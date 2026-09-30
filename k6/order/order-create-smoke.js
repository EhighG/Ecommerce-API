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
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/order-create-smoke.js",
  );
}

const ORDER_CANDIDATES_FILE =
  __ENV.ORDER_CANDIDATES_FILE || "./data/order_create_candidates.csv";
const iterations = Number(__ENV.ITERATIONS || 3);
const vus = Number(__ENV.VUS || 1);

const orderCandidates = new SharedArray("order create candidates", function () {
  return parseCandidateCsv(open(ORDER_CANDIDATES_FILE));
});

if (orderCandidates.length === 0) {
  throw new Error(
    `${ORDER_CANDIDATES_FILE} must contain at least one candidate`,
  );
}

export const options = {
  scenarios: {
    order_create_smoke: {
      executor: "shared-iterations",
      vus,
      iterations,
      maxDuration: __ENV.MAX_DURATION || "2m",
    },
  },
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:POST /orders}": ["p(95)<2000"],
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
