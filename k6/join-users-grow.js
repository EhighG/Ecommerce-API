import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { scenario } from "k6/execution";
import {
  getCsrf,
  logUnexpectedResponse,
  withCsrfHeaders,
} from "./lib/auth.js";
import { env } from "./lib/env.js";

const BASE_URL = env("BASE_URL");
const PASSWORD = env("PASSWORD");
const ROLE = "BUYER";
const RATE = Number(__ENV.JOIN_RATE || 30);
const DURATION = __ENV.DURATION || "10m";
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 100);
const MAX_VUS = Number(__ENV.MAX_VUS || 300);

const baseUsers = new SharedArray("available buyer emails", function () {
  return JSON.parse(open("./data/available_buyer_email.json")).map((row, index) => ({
    id: row.id,
    index,
  }));
});

function toAlphaLabel(index) {
  let value = index + 1;
  let label = "";

  while (value > 0) {
    value -= 1;
    label = String.fromCharCode(97 + (value % 26)) + label;
    value = Math.floor(value / 26);
  }

  return label;
}

function buildNickname(domainLabel, suffix) {
  return `abc${suffix}${domainLabel}`.slice(0, 20);
}

function buildJoinUser(iteration) {
  const userIndex = Math.floor(iteration / 20) % baseUsers.length;
  const suffix = (iteration % 20) + 1;
  const baseUser = baseUsers[userIndex];
  const domainLabel = toAlphaLabel(baseUser.index);
  const email = `abc${suffix}@${domainLabel}.com`;

  return {
    email,
    nickname: buildNickname(domainLabel, suffix),
    password: PASSWORD,
    passwordCheck: PASSWORD,
    role: ROLE,
  };
}

export const options = {
  scenarios: {
    grow_users: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const user = buildJoinUser(scenario.iterationInTest);
  const csrf = getCsrf(BASE_URL);

  const joinRes = http.post(`${BASE_URL}/users`, JSON.stringify(user), {
    headers: withCsrfHeaders(csrf, {
      "Content-Type": "application/json",
    }),
    tags: { name: "POST /users" },
  });

  const joined = check(joinRes, {
    "join status is 200": (r) => r.status === 200,
  });

  if (!joined) {
    logUnexpectedResponse("join grow failed", joinRes, {
      email: user.email,
      role: user.role,
    });
    fail("join grow failed");
  }
}
