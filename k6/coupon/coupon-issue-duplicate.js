import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { Counter } from "k6/metrics";
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
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/coupon/coupon-issue-duplicate.js",
  );
}

const BUYERS_FILE = __ENV.BUYERS_FILE || "../data/available_buyer_email.json";
const CANDIDATE_INDEX = Number(__ENV.CANDIDATE_INDEX || 0);
const COUPON_EVENT_ID = Number(__ENV.COUPON_EVENT_ID);
const EXPECT_INITIAL_SUCCESS =
  (__ENV.EXPECT_INITIAL_SUCCESS || "true").toLowerCase() !== "false";

if (!Number.isInteger(COUPON_EVENT_ID) || COUPON_EVENT_ID <= 0) {
  throw new Error("COUPON_EVENT_ID is required. Example: COUPON_EVENT_ID=10");
}

// Load profiles. Keep one profile active by env vars, or uncomment the
// matching constants below and comment out the env-driven constants.
//
// Smoke:
// const vus = 1;
// const iterations = 5;
// const maxDuration = "1m";
//
// Same-user sequential duplicate:
// const vus = 1;
// const iterations = 30;
// const maxDuration = "2m";
//
// Same-user concurrent duplicate:
// const vus = 20;
// const iterations = 100;
// const maxDuration = "2m";
//
// Higher contention:
// const vus = 50;
// const iterations = 300;
// const maxDuration = "3m";

const vus = Number(__ENV.VUS || 20);
const iterations = Number(__ENV.ITERATIONS || 100);
const maxDuration = __ENV.MAX_DURATION || "2m";

const couponDuplicateSuccess = new Counter("coupon_duplicate_success");
const couponDuplicateExpectedRejection = new Counter(
  "coupon_duplicate_expected_rejection",
);
const couponDuplicateUnexpectedFailure = new Counter(
  "coupon_duplicate_unexpected_failure",
);

// 서버 오류 code는 4자리 문자열이다.
const COUPON_ALREADY_ISSUED = "7506";

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

if (CANDIDATE_INDEX < 0 || CANDIDATE_INDEX >= buyers.length) {
  throw new Error(`CANDIDATE_INDEX out of range: ${CANDIDATE_INDEX}`);
}

export const options = {
  noConnectionReuse: false,
  noVUConnectionReuse: true,
  scenarios: {
    coupon_issue_duplicate: {
      executor: "shared-iterations",
      vus,
      iterations,
      maxDuration,
    },
  },
  thresholds: {
    checks: ["rate>0.95"],
    coupon_duplicate_unexpected_failure: ["count==0"],
    coupon_duplicate_success: [
      EXPECT_INITIAL_SUCCESS ? "count==1" : "count==0",
    ],
    dropped_iterations: ["count==0"],
  },
};

function targetCandidate() {
  const candidate = buyers[CANDIDATE_INDEX];
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

function logDuplicateAttempt(label, candidate, res, extra = {}) {
  const apiError = readApiError(res);
  const body = parseJson(res);

  console.log(
    JSON.stringify({
      label,
      email: candidate.email,
      userId: candidate.userId,
      couponEventId: candidate.couponEventId,
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
  const candidate = targetCandidate();
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
      couponEventId: candidate.couponEventId,
    });
    logCandidateFailure("login failed", candidate, loginResult.res);
    fail("login failed");
  }

  const issueRes = postJsonWithCsrfRetry(
    BASE_URL,
    `/coupons/events/${candidate.couponEventId}/issue`,
    null,
    csrfRef,
    { tags: { name: "POST /coupons/events/{id}/issue" } },
  );

  if (issueRes.status === 200) {
    couponDuplicateSuccess.add(1);
    const body = parseJson(issueRes);
    const ok = check(issueRes, {
      "duplicate test success status is 200": (r) => r.status === 200,
      "duplicate test success returns id": () =>
        Number.isInteger(Number(body?.couponIssuedId)),
    });
    logDuplicateAttempt("coupon duplicate success", candidate, issueRes);
    if (!ok) {
      logCandidateFailure(
        "coupon duplicate success response invalid",
        candidate,
        issueRes,
      );
      fail("coupon duplicate success response invalid");
    }
    return;
  }

  const apiError = readApiError(issueRes);
  if (issueRes.status === 400 && apiError?.code === COUPON_ALREADY_ISSUED) {
    couponDuplicateExpectedRejection.add(1);
    check(issueRes, {
      "duplicate rejection status is 400": (r) => r.status === 400,
    });
    logDuplicateAttempt(
      "coupon duplicate expected rejection",
      candidate,
      issueRes,
    );
    return;
  }

  couponDuplicateUnexpectedFailure.add(1);
  logUnexpectedResponse("unexpected coupon duplicate failure", issueRes, {
    email: candidate.email,
    couponEventId: candidate.couponEventId,
  });
  logCandidateFailure(
    "unexpected coupon duplicate failure",
    candidate,
    issueRes,
  );
  fail("unexpected coupon duplicate failure");
}
