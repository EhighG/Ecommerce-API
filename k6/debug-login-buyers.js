import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import { getCsrf, login, logUnexpectedResponse } from "./lib/auth.js";
import { env } from "./lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/debug-login-buyers.js",
  );
}

const BUYER_PASSWORD = env("PASSWORD");
const iterations = Number(__ENV.ITERATIONS || 50);
const vus = Number(__ENV.VUS || 1);

const buyers = new SharedArray("available buyer emails", function () {
  return JSON.parse(open("./data/available_buyer_email.json")).map((row) => ({
    email: row.email,
    password: BUYER_PASSWORD,
  }));
});

if (buyers.length === 0) {
  throw new Error("available_buyer_email.json must contain at least one email");
}

export const options = {
  scenarios: {
    debug_login_buyers: {
      executor: "shared-iterations",
      vus,
      iterations,
      maxDuration: __ENV.MAX_DURATION || "5m",
    },
  },
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate==0"],
  },
};

export default function () {
  const buyer = buyers[scenario.iterationInTest % buyers.length];
  const csrf = getCsrf(BASE_URL, { failOnError: false });

  const csrfOk = check(csrf, {
    "csrf token exists before login": (token) =>
      typeof token?.headerName === "string" &&
      token.headerName.length > 0 &&
      typeof token?.token === "string" &&
      token.token.length > 0,
  });

  if (!csrfOk) {
    fail(`csrf failed: email=${buyer.email}`);
  }

  const { res } = login(BASE_URL, buyer.email, buyer.password, {
    csrf,
    failOnError: false,
  });

  const loginOk = check(res, {
    "login status is 200": (r) => r.status === 200,
  });

  if (!loginOk) {
    logUnexpectedResponse("login failed", res, {
      email: buyer.email,
    });
    fail(`login failed: email=${buyer.email}, status=${res.status}`);
  }
}
