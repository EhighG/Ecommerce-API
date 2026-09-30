import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { Counter } from "k6/metrics";
import { parseOrderCouponCandidateCsv } from "../lib/coupon-candidates.js";
import { logUnexpectedResponse } from "../lib/auth.js";
import { env, passwordOrEnv } from "../lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/260518_order_seperated_from_auth/order-coupon-auth-sessions.js",
  );
}

const ORDER_COUPON_CANDIDATES_FILE =
  __ENV.ORDER_COUPON_CANDIDATES_FILE ||
  "../data/order_coupon_mixed_candidates.csv";

const authSessionSuccess = new Counter("auth_session_success");
const authSessionFailure = new Counter("auth_session_failure");

const candidates = new SharedArray(
  "order coupon mixed candidates for auth sessions",
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
  setupTimeout: __ENV.PREAUTH_SETUP_TIMEOUT || "30m",
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    auth_sessions_noop: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
    },
  },
  thresholds: {
    checks: ["rate>0.999"],
    auth_session_failure: ["count==0"],
  },
};

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function cookieHeaderFromJar() {
  const cookies = http.cookieJar().cookiesForURL(`${BASE_URL}/orders`);

  return Object.entries(cookies)
    .filter(([, values]) => values.length > 0)
    .map(([name, values]) => `${name}=${values[0]}`)
    .join("; ");
}

function uniqueAuthCandidates() {
  const byEmail = new Map();

  for (const candidate of candidates) {
    const password = passwordOrEnv(candidate.password);
    const previous = byEmail.get(candidate.email);

    if (previous && previous.password !== password) {
      throw new Error(
        `candidate email has multiple passwords: email=${candidate.email}`,
      );
    }

    if (!previous) {
      byEmail.set(candidate.email, {
        email: candidate.email,
        password,
        userId: candidate.userId,
      });
    }
  }

  return Array.from(byEmail.values());
}

function createSession(candidate) {
  const jar = http.cookieJar();
  jar.clear(BASE_URL);
  jar.clear(`${BASE_URL}/auth/csrf`);
  jar.clear(`${BASE_URL}/orders`);

  const csrfRes = http.get(`${BASE_URL}/auth/csrf`, {
    tags: { name: "PREAUTH GET /auth/csrf" },
  });
  const csrf = parseJson(csrfRes);
  const csrfOk = check(csrfRes, {
    "preauth csrf status is 200": (r) => r.status === 200,
    "preauth csrf headerName exists": () =>
      typeof csrf?.headerName === "string" && csrf.headerName.length > 0,
    "preauth csrf token exists": () =>
      typeof csrf?.token === "string" && csrf.token.length > 0,
  });

  if (!csrfOk) {
    authSessionFailure.add(1);
    logUnexpectedResponse("preauth csrf failed", csrfRes, {
      email: candidate.email,
    });
    fail("preauth csrf failed");
  }

  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: candidate.email, password: candidate.password }),
    {
      headers: {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      tags: { name: "PREAUTH POST /auth/login" },
    },
  );
  const loginOk = check(loginRes, {
    "preauth login status is 200": (r) => r.status === 200,
  });

  if (!loginOk) {
    authSessionFailure.add(1);
    logUnexpectedResponse("preauth login failed", loginRes, {
      email: candidate.email,
    });
    fail("preauth login failed");
  }

  const cookieHeader = cookieHeaderFromJar();

  if (!cookieHeader.includes("JSESSIONID=")) {
    authSessionFailure.add(1);
    fail(`preauth login did not return JSESSIONID: email=${candidate.email}`);
  }

  authSessionSuccess.add(1);

  return {
    email: candidate.email,
    userId: candidate.userId,
    cookieHeader,
    csrf: {
      headerName: csrf.headerName,
      token: csrf.token,
    },
  };
}

export function setup() {
  const sessions = {};
  const authCandidates = uniqueAuthCandidates();

  for (const candidate of authCandidates) {
    sessions[candidate.email] = createSession(candidate);
  }

  return {
    generatedAt: new Date().toISOString(),
    baseUrl: BASE_URL,
    candidatesFile: ORDER_COUPON_CANDIDATES_FILE,
    sessionCount: authCandidates.length,
    sessions,
  };
}

export default function (preauth) {
  console.log(JSON.stringify(preauth, null, 2));
}

export function handleSummary() {
  return {
    stdout: "",
  };
}
