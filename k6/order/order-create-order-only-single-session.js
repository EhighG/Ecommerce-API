import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import { parseCandidateCsv } from "../lib/candidates.js";
import { logUnexpectedResponse } from "../lib/auth.js";
import { env } from "../lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/order-create-order-only-single-session.js",
  );
}

const ORDER_CANDIDATES_FILE =
  __ENV.ORDER_CANDIDATES_FILE || "./data/order_only_single_user_candidates.csv";
const ORDER_USER_EMAIL = __ENV.ORDER_USER_EMAIL;
const ORDER_USER_PASSWORD = env("PASSWORD");

// Load profiles. Keep one profile active by env vars, or uncomment the
// matching constants below and comment out the env-driven constants.
//
// Baseline:
// const orderRate = 10;
// const duration = "1m";
// const preAllocatedVUs = 50;
// const maxVUs = 200;
//
// Stress:
const orderRate = 20;
const duration = "3m";
const preAllocatedVUs = 100;
const maxVUs = 400;
//
// Limit search:
// const orderRate = 30;
// const duration = "1m";
// const preAllocatedVUs = 150;
// const maxVUs = 600;

// const orderRate = Number(__ENV.ORDER_RATE || 10);
// const duration = __ENV.DURATION || "1m";
// const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 50);
// const maxVUs = Number(__ENV.MAX_VUS || 200);

const p95Ms = Number(__ENV.ORDER_P95_MS || 3000);
const p99Ms = Number(__ENV.ORDER_P99_MS || 8000);

const orderCandidates = new SharedArray(
  "single-session order candidates",
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
    order_create_order_only_single_session: {
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

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function collectCookies(...responses) {
  const cookies = new Map();

  for (const res of responses) {
    for (const [name, values] of Object.entries(res.cookies || {})) {
      if (values.length > 0) {
        cookies.set(name, values[0].value);
      }
    }
  }

  return Array.from(cookies.entries())
    .map(([name, value]) => `${name}=${value}`)
    .join("; ");
}

function buildOrderReq(candidate) {
  return {
    items: candidate.items.map((item) => ({
      cartItemId: item.cartItemId,
      orderQuantity: item.orderQuantity,
    })),
  };
}

function pickCandidate() {
  const index = scenario.iterationInTest;

  if (index >= orderCandidates.length) {
    fail(
      `not enough order candidates: iteration=${index}, candidates=${orderCandidates.length}`,
    );
  }

  return orderCandidates[index];
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

export function setup() {
  const loginEmail = ORDER_USER_EMAIL || orderCandidates[0].email;
  const csrfRes = http.get(`${BASE_URL}/auth/csrf`, {
    tags: { name: "GET /auth/csrf" },
  });
  const csrf = parseJson(csrfRes);
  const csrfOk = check(csrfRes, {
    "setup csrf status is 200": (r) => r.status === 200,
    "setup csrf headerName exists": () => typeof csrf?.headerName === "string",
    "setup csrf token exists": () => typeof csrf?.token === "string",
  });

  if (!csrfOk) {
    logUnexpectedResponse("setup csrf failed", csrfRes, { email: loginEmail });
    fail("setup csrf failed");
  }

  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: loginEmail, password: ORDER_USER_PASSWORD }),
    {
      headers: {
        "Content-Type": "application/json",
        Cookie: collectCookies(csrfRes),
        [csrf.headerName]: csrf.token,
      },
      tags: { name: "POST /auth/login" },
    },
  );
  const loginOk = check(loginRes, {
    "setup login status is 200": (r) => r.status === 200,
  });

  if (!loginOk) {
    logUnexpectedResponse("setup login failed", loginRes, {
      email: loginEmail,
    });
    fail("setup login failed");
  }

  return {
    email: loginEmail,
    csrf,
    cookieHeader: collectCookies(csrfRes, loginRes),
  };
}

export default function (session) {
  const candidate = pickCandidate();
  const orderReq = buildOrderReq(candidate);
  const orderRes = http.post(`${BASE_URL}/orders`, JSON.stringify(orderReq), {
    headers: {
      "Content-Type": "application/json",
      Cookie: session.cookieHeader,
      [session.csrf.headerName]: session.csrf.token,
    },
    tags: { name: "POST /orders" },
  });
  const orderId = Number(orderRes.body);
  const ok = check(orderRes, {
    "create order status is 200": (r) => r.status === 200,
    "create order returns order id": () => Number.isInteger(orderId),
  });

  if (!ok) {
    logUnexpectedResponse("create order failed", orderRes, {
      loginEmail: session.email,
      candidateEmail: candidate.email,
      candidateIndex: scenario.iterationInTest,
    });
    logCandidateFailure(
      "create order failed",
      candidate,
      scenario.iterationInTest,
      {
        loginEmail: session.email,
        status: orderRes.status,
        requestBody: orderReq,
        responseBody: orderRes.body,
      },
    );
    fail("create order failed");
  }
}
