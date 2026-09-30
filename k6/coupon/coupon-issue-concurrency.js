import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { Counter } from "k6/metrics";
import { scenario } from "k6/execution";
import {
  getCsrf,
  login,
  logUnexpectedResponse,
  postJsonWithCsrfRetry,
  readApiError,
} from "../lib/auth.js";
import { env, passwordOrEnv } from "../lib/env.js";

const BASE_URL = env("BASE_URL");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/coupon-issue-concurrency.js",
  );
}

const BUYERS_FILE = __ENV.BUYERS_FILE || "../data/available_buyer_email.json";
const COUPON_EVENT_ID = Number(__ENV.COUPON_EVENT_ID);

if (!Number.isInteger(COUPON_EVENT_ID) || COUPON_EVENT_ID <= 0) {
  throw new Error("COUPON_EVENT_ID is required. Example: COUPON_EVENT_ID=10");
}

// Load profiles. Keep one profile active by env vars, or uncomment the
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
// Target sold-out correctness:
// - Set coupon_event.initial_quantity to 100 before running.
// - Prepare at least 300 buyer rows in available_buyer_email.json.
// - Expected: 100 success, 200 sold-out rejections.
// const vus = 100;
// const iterations = 300;
// const maxDuration = "3m";
//
// Higher contention:
// const vus = 200;
// const iterations = 600;
// const maxDuration = "5m";

const vus = Number(__ENV.VUS || 100);
const iterations = Number(__ENV.ITERATIONS || 300);
const maxDuration = __ENV.MAX_DURATION || "3m";

const couponIssueSuccess = new Counter("coupon_issue_success");
const couponIssueSoldOut = new Counter("coupon_issue_sold_out");
const couponIssueDuplicate = new Counter("coupon_issue_duplicate");
const couponIssueUnexpectedFailure = new Counter(
  "coupon_issue_unexpected_failure",
);

const COUPON_ALREADY_ISSUED = 7506;
const COUPON_SOLD_OUT = 7507;

const buyers = new SharedArray("available buyer users", function () {
  return JSON.parse(open(BUYERS_FILE)).map((row) => ({
    userId: row.id,
    email: row.email,
    password: row.password,
  }));
});

if (buyers.length === 0) {
  throw new Error(`${BUYERS_FILE} must contain at least one buyer`);
}

export const options = {
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    coupon_issue_concurrency: {
      executor: "shared-iterations",
      vus,
      iterations,
      maxDuration,
    },
  },
  thresholds: {
    checks: ["rate>0.95"],
    coupon_issue_unexpected_failure: ["count==0"],
    dropped_iterations: ["count==0"],
  },
};

function pickCandidate() {
  const index = scenario.iterationInTest;

  if (index >= buyers.length) {
    fail(`not enough buyers: iteration=${index}, buyers=${buyers.length}`);
  }

  const candidate = buyers[index];
  return {
    ...candidate,
    couponEventId: COUPON_EVENT_ID,
  };
}

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function logIssueAttempt(
  label,
  candidate,
  res,
  startedAt,
  endedAt,
  extra = {},
) {
  const apiError = readApiError(res);
  const body = parseJson(res);

  console.log(
    JSON.stringify({
      label,
      iteration: scenario.iterationInTest,
      email: candidate.email,
      userId: candidate.userId,
      couponEventId: candidate.couponEventId,
      requestStartedAt: startedAt,
      responseEndedAt: endedAt,
      status: res.status,
      errorCode: apiError?.code,
      errorMessage: apiError?.message,
      couponIssuedId: body?.couponIssuedId,
      issuedAt: body?.issuedAt,
      ...extra,
    }),
  );
}

function logCandidateFailure(label, candidate, res, extra = {}) {
  console.error(
    JSON.stringify({
      label,
      iteration: scenario.iterationInTest,
      email: candidate.email,
      userId: candidate.userId,
      couponEventId: candidate.couponEventId,
      status: res.status,
      responseBody: res.body,
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
      iteration: scenario.iterationInTest,
    });
    logCandidateFailure("login failed", candidate, loginResult.res);
    fail("login failed");
  }

  const startedAt = new Date().toISOString();
  const issueRes = postJsonWithCsrfRetry(
    BASE_URL,
    `/coupons/events/${candidate.couponEventId}/issue`,
    null,
    csrfRef,
    { tags: { name: "POST /coupons/events/{id}/issue" } },
  );
  const endedAt = new Date().toISOString();

  if (issueRes.status === 200) {
    couponIssueSuccess.add(1);
    const body = parseJson(issueRes);
    const ok = check(issueRes, {
      "coupon issue status is 200": (r) => r.status === 200,
      "coupon issue returns id": () =>
        Number.isInteger(Number(body?.couponIssuedId)),
      "coupon issue returns issuedAt": () => typeof body?.issuedAt === "string",
    });
    logIssueAttempt(
      "coupon issue success",
      candidate,
      issueRes,
      startedAt,
      endedAt,
    );
    if (!ok) {
      logCandidateFailure(
        "coupon issue success response invalid",
        candidate,
        issueRes,
        {
          responseBody: issueRes.body,
        },
      );
      fail("coupon issue success response invalid");
    }
    return;
  }

  const apiError = readApiError(issueRes);
  if (issueRes.status === 400 && apiError?.code === COUPON_SOLD_OUT) {
    couponIssueSoldOut.add(1);
    check(issueRes, {
      "coupon sold out status is 400": (r) => r.status === 400,
    });
    logIssueAttempt(
      "coupon issue sold out",
      candidate,
      issueRes,
      startedAt,
      endedAt,
    );
    return;
  }

  if (issueRes.status === 400 && apiError?.code === COUPON_ALREADY_ISSUED) {
    couponIssueDuplicate.add(1);
    check(issueRes, {
      "coupon duplicate status is 400": (r) => r.status === 400,
    });
    logIssueAttempt(
      "coupon issue duplicate",
      candidate,
      issueRes,
      startedAt,
      endedAt,
    );
    return;
  }

  couponIssueUnexpectedFailure.add(1);
  logUnexpectedResponse("unexpected coupon issue failure", issueRes, {
    email: candidate.email,
    iteration: scenario.iterationInTest,
    couponEventId: candidate.couponEventId,
  });
  logCandidateFailure("unexpected coupon issue failure", candidate, issueRes);
  fail("unexpected coupon issue failure");
}
