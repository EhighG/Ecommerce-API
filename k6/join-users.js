import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import {
  getCsrf,
  login,
  logUnexpectedResponse,
  withCsrfHeaders,
} from "./lib/auth.js";
import { env } from "./lib/env.js";

const BASE_URL = env("BASE_URL");
const PASSWORD = env("PASSWORD");

const users = new SharedArray("join users", function () {
  return JSON.parse(open("./data/mock_nickname_email.json")).map(
    (row, index) => ({
      email: row.email,
      nickname: row.nickname,
      password: PASSWORD,
      passwordCheck: PASSWORD,
      role: index < 80 ? "SELLER" : "BUYER",
    }),
  );
});

const vus = Number(__ENV.VUS || 10);
const iterations = Number(__ENV.JOIN_ITERATIONS || users.length);
const maxDuration = __ENV.MAX_DURATION || "10m";

export const options = {
  scenarios: {
    join_users: {
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
  const user = users[scenario.iterationInTest % users.length];
  const csrf = getCsrf(BASE_URL);

  const joinRes = http.post(`${BASE_URL}/users`, JSON.stringify(user), {
    headers: withCsrfHeaders(csrf, {
      "Content-Type": "application/json",
    }),
    tags: { name: "POST /users" },
  });

  const joined = check(joinRes, {
    "join status is 200": (r) => r.status === 200,
    "join returns user id": (r) =>
      r.body.length > 0 && Number.isInteger(Number(r.body)),
  });

  if (!joined) {
    logUnexpectedResponse("join failed", joinRes, {
      email: user.email,
      role: user.role,
    });
    fail("join failed");
  }

  const loginResult = login(BASE_URL, user.email, user.password, {
    failOnError: false,
  });
  const loggedIn = check(loginResult.res, {
    "login after join status is 200": (r) => r.status === 200,
  });

  if (!loggedIn) {
    logUnexpectedResponse("login after join failed", loginResult.res, {
      email: user.email,
      role: user.role,
    });
    fail("login after join failed");
  }
}
