import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import {
  getCsrf,
  login,
  logUnexpectedResponse,
  logout,
} from "./lib/auth.js";
import { parseLoginUserCsv } from "./lib/login-users.js";
import { env, optionalEnv } from "./lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/login-only.js",
  );
}

const LOGIN_USERS_FILE = __ENV.LOGIN_USERS_FILE || "./data/login_users.csv";
const DEFAULT_PASSWORD = optionalEnv("PASSWORD");
const DO_LOGOUT = __ENV.DO_LOGOUT !== "false";

// Load profiles. Keep one profile active by env vars, or uncomment the
// matching constants below and comment out the env-driven constants.
//
// Baseline:
// const loginRate = 10;
// const duration = "1m";
// const preAllocatedVUs = 50;
// const maxVUs = 200;
//
// Previous order-test equivalent:
const loginRate = 20;
const duration = "3m";
const preAllocatedVUs = 100;
const maxVUs = 300;
//
// Stress:
// const loginRate = 30;
// const duration = "3m";
// const preAllocatedVUs = 150;
// const maxVUs = 500;

// const loginRate = Number(__ENV.LOGIN_RATE || 20);
// const duration = __ENV.DURATION || "1m";
// const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 100);
// const maxVUs = Number(__ENV.MAX_VUS || 300);


const loginP95Ms = Number(__ENV.LOGIN_P95_MS || 3000);
const loginP99Ms = Number(__ENV.LOGIN_P99_MS || 8000);

const users = new SharedArray("login users", function () {
  return parseLoginUserCsv(open(LOGIN_USERS_FILE), DEFAULT_PASSWORD);
});

if (users.length === 0) {
  throw new Error(`${LOGIN_USERS_FILE} must contain at least one user`);
}

export const options = {
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    login_only: {
      executor: "constant-arrival-rate",
      rate: loginRate,
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
    "http_req_duration{name:POST /auth/login}": [
      `p(95)<${loginP95Ms}`,
      `p(99)<${loginP99Ms}`,
    ],
  },
};

function pickUser() {
  return users[scenario.iterationInTest % users.length];
}

function logLoginFailure(label, user, extra = {}) {
  console.error(
    JSON.stringify({
      label,
      iteration: scenario.iterationInTest,
      email: user.email,
      ...extra,
    }),
  );
}

export default function () {
  const user = pickUser();
  const csrfRef = { value: getCsrf(BASE_URL) };
  const loginResult = login(BASE_URL, user.email, user.password, {
    csrf: csrfRef.value,
    failOnError: false,
  });
  const loginOk = check(loginResult.res, {
    "login status is 200": (r) => r.status === 200,
  });

  if (!loginOk) {
    logUnexpectedResponse("login failed", loginResult.res, {
      email: user.email,
      iteration: scenario.iterationInTest,
    });
    logLoginFailure("login failed", user, {
      status: loginResult.res.status,
      responseBody: loginResult.res.body,
    });
    fail("login failed");
  }

  if (!DO_LOGOUT) {
    return;
  }

  const logoutResult = logout(BASE_URL, {
    csrfRef,
    failOnError: false,
  });
  const logoutOk = check(logoutResult.res, {
    "logout status is 200": (r) => r.status === 200,
  });

  if (!logoutOk) {
    logUnexpectedResponse("logout failed", logoutResult.res, {
      email: user.email,
      iteration: scenario.iterationInTest,
    });
    logLoginFailure("logout failed", user, {
      status: logoutResult.res.status,
      responseBody: logoutResult.res.body,
    });
    fail("logout failed");
  }
}
