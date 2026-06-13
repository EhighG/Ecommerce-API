import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { Counter, Trend } from "k6/metrics";
import { scenario } from "k6/execution";
import { parseOrderCouponCandidateCsv } from "../lib/coupon-candidates.js";
import { logUnexpectedResponse, readApiError } from "../lib/auth.js";
import { env } from "../lib/env.js";

const BASE_URL = env("BASE_URL");
const LOADTEST_AUTH_SECRET = env("LOADTEST_AUTH_SECRET");

if (!BASE_URL) {
  throw new Error(
    "BASE_URL is required. Example: BASE_URL=http://host/api k6 run k6/260519_order_bypass_auth/order-coupon-mixed.js",
  );
}

const ORDER_COUPON_CANDIDATES_FILE =
  __ENV.ORDER_COUPON_CANDIDATES_FILE ||
  "../data/order_coupon_mixed_candidates.csv";
const CANCEL_RATIO = Number(__ENV.CANCEL_RATIO || 0.3);
const TEST_MODE = (__ENV.TEST_MODE || "fixed").toLowerCase();
const ORDER_CREATE_P99_MS = Number(__ENV.ORDER_CREATE_P99_MS || 1500);
const ORDER_CANCEL_P99_MS = Number(__ENV.ORDER_CANCEL_P99_MS || 1500);
const ORDER_DETAIL_P99_MS = Number(__ENV.ORDER_DETAIL_P99_MS || 500);
const HTTP_REQ_FAILED_RATE = Number(__ENV.HTTP_REQ_FAILED_RATE || 0.001);

const orderRate = Number(__ENV.ORDER_RATE || 30);
const duration = __ENV.DURATION || "3m";
const WARMUP_ENABLED = booleanEnv("WARMUP_ENABLED", true);
const WARMUP_RATE = Number(
  __ENV.WARMUP_RATE || Math.max(1, Math.round(orderRate * 0.5)),
);
const WARMUP_DURATION = __ENV.WARMUP_DURATION || "3m";
const REST_DURATION = __ENV.REST_DURATION || "30s";
const FAILURE_DETAIL_LOGGING_ENABLED = booleanEnv(
  "FAILURE_DETAIL_LOGGING_ENABLED",
  false,
);
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 300);
const maxVUs = Number(__ENV.MAX_VUS || 800);

const CAPACITY_RATES = parseNumberList(
  __ENV.CAPACITY_RATES || "10,20,30,40,50,60",
);
const CAPACITY_STAGE_DURATION = __ENV.CAPACITY_STAGE_DURATION || "3m";
const CAPACITY_REST_DURATION = __ENV.CAPACITY_REST_DURATION || REST_DURATION;
const CAPACITY_GRACEFUL_STOP = __ENV.CAPACITY_GRACEFUL_STOP || "30s";
const capacityPreAllocatedVUs = Number(
  __ENV.CAPACITY_PRE_ALLOCATED_VUS || preAllocatedVUs,
);
const capacityMaxVUs = Number(__ENV.CAPACITY_MAX_VUS || maxVUs);

const orderCouponOrderSuccess = new Counter("order_coupon_order_success");
const orderCouponCancelSuccess = new Counter("order_coupon_cancel_success");
const orderCouponExpectedFailure = new Counter("order_coupon_expected_failure");
const orderCouponUnexpectedFailure = new Counter(
  "order_coupon_unexpected_failure",
);
const orderCouponMissingUserId = new Counter("order_coupon_missing_user_id");
const orderCouponErrorTotal = new Counter("order_coupon_error_total");
const orderCouponPostOrdersDuration = new Trend(
  "order_coupon_post_orders_duration",
  true,
);
const orderCouponGetOrderDetailDuration = new Trend(
  "order_coupon_get_order_detail_duration",
  true,
);
const orderCouponCancelOrderItemDuration = new Trend(
  "order_coupon_cancel_order_item_duration",
  true,
);
const orderCouponGetOrderDetailAfterCancelDuration = new Trend(
  "order_coupon_get_order_detail_after_cancel_duration",
  true,
);

const EXPECTED_FAILURE_CODES = new Set([
  2501, // INSUFFICIENT_INVENTORY
  3003, // WRONG_STATUS_CHANGE
  7000, // CART_ITEM_NOT_FOUND
  7501, // COUPON_EXPIRED
  7502, // INVALID_COUPON_STATUS
  7509, // DUPLICATED_COUPON
  7510, // COUPON_ISSUED_NOT_FOUND
]);

const ERROR_TYPES = [
  "missing_user_id",
  "expected_order_failure",
  "expected_cancel_failure",
  "unexpected_order_failure",
  "unexpected_cancel_failure",
  "invalid_order_response",
  "invalid_detail_response",
  "invalid_cancel_response",
  "invalid_cancel_detail_response",
];

const candidates = new SharedArray(
  "order coupon mixed candidates",
  function () {
    return parseOrderCouponCandidateCsv(open(ORDER_COUPON_CANDIDATES_FILE));
  },
);

if (candidates.length === 0) {
  throw new Error(
    `${ORDER_COUPON_CANDIDATES_FILE} must contain at least one candidate`,
  );
}

const scenarioSchedules = buildScenarioSchedules();
const scenarioIterationOffsets = buildScenarioIterationOffsets();

export const options = {
  noConnectionReuse: false, // conn reuse on
  noVUConnectionReuse: true,
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  scenarios: buildScenarios(),
  thresholds: buildThresholds(),
};

function parseNumberList(value) {
  const numbers = String(value)
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isFinite(item) && item > 0);

  if (numbers.length === 0) {
    throw new Error(`expected positive number list, got: ${value}`);
  }

  return numbers;
}

function booleanEnv(name, defaultValue) {
  const raw = __ENV[name];
  if (raw === undefined) {
    return defaultValue;
  }

  return ["1", "true", "yes", "y", "on"].includes(
    String(raw).trim().toLowerCase(),
  );
}

function parseDurationSeconds(value) {
  const match = String(value).match(/^(\d+)(ms|s|m|h)$/);
  if (!match) {
    throw new Error(`unsupported duration: ${value}`);
  }

  const amount = Number(match[1]);
  const unit = match[2];
  if (unit === "ms") {
    return Math.ceil(amount / 1000);
  }
  if (unit === "s") {
    return amount;
  }
  if (unit === "m") {
    return amount * 60;
  }
  return amount * 60 * 60;
}

function addSeconds(date, seconds) {
  return new Date(date.getTime() + seconds * 1000);
}

function formatTime(date) {
  return `${date.toString()} (${date.toISOString()})`;
}

function plannedIterations(rate, durationSeconds) {
  return Math.floor(rate * durationSeconds) + 1;
}

function buildScenarioSchedules() {
  if (TEST_MODE === "fixed") {
    const durationSeconds = parseDurationSeconds(duration);
    const schedules = [];
    let startOffsetSeconds = 0;

    if (WARMUP_ENABLED) {
      const warmupDurationSeconds = parseDurationSeconds(WARMUP_DURATION);
      const restDurationSeconds = parseDurationSeconds(REST_DURATION);
      schedules.push({
        name: "order_coupon_mixed_fixed_warmup",
        phase: "warmup",
        includeInThresholds: false,
        rate: WARMUP_RATE,
        startOffsetSeconds,
        durationText: WARMUP_DURATION,
        durationSeconds: warmupDurationSeconds,
        plannedIterations: plannedIterations(
          WARMUP_RATE,
          warmupDurationSeconds,
        ),
        restAfterSeconds: restDurationSeconds,
      });
      startOffsetSeconds += warmupDurationSeconds + restDurationSeconds;
    }

    schedules.push(
      {
        name: "order_coupon_mixed_fixed_measure",
        phase: "measure",
        includeInThresholds: true,
        rate: orderRate,
        startOffsetSeconds,
        durationText: duration,
        durationSeconds,
        plannedIterations: plannedIterations(orderRate, durationSeconds),
        restAfterSeconds: 0,
      },
    );

    return schedules;
  }

  if (TEST_MODE !== "capacity") {
    throw new Error(`unsupported TEST_MODE: ${TEST_MODE}`);
  }

  const stageDurationSeconds = parseDurationSeconds(CAPACITY_STAGE_DURATION);
  const restDurationSeconds = parseDurationSeconds(CAPACITY_REST_DURATION);
  let startOffsetSeconds = 0;

  return CAPACITY_RATES.map((rate, index) => {
    const isLast = index === CAPACITY_RATES.length - 1;
    const item = {
      name: capacityScenarioName(rate, index),
      phase: "measure",
      includeInThresholds: true,
      rate,
      startOffsetSeconds,
      durationText: CAPACITY_STAGE_DURATION,
      durationSeconds: stageDurationSeconds,
      plannedIterations: plannedIterations(rate, stageDurationSeconds),
      restAfterSeconds: isLast ? 0 : restDurationSeconds,
    };
    startOffsetSeconds += stageDurationSeconds + item.restAfterSeconds;
    return item;
  });
}

function buildFixedScenarios() {
  return Object.fromEntries(
    scenarioSchedules.map((schedule) => [
      schedule.name,
      {
        executor: "constant-arrival-rate",
        rate: schedule.rate,
        timeUnit: "1s",
        duration: schedule.durationText,
        startTime: `${schedule.startOffsetSeconds}s`,
        preAllocatedVUs,
        maxVUs,
      },
    ]),
  );
}

function capacityScenarioName(rate, index) {
  return `order_coupon_mixed_capacity_${index + 1}_${rate}ips`;
}

function buildCapacityScenarios() {
  return Object.fromEntries(
    scenarioSchedules.map((schedule) => [
      schedule.name,
      {
        executor: "constant-arrival-rate",
        rate: schedule.rate,
        timeUnit: "1s",
        duration: schedule.durationText,
        gracefulStop: CAPACITY_GRACEFUL_STOP,
        startTime: `${schedule.startOffsetSeconds}s`,
        preAllocatedVUs: capacityPreAllocatedVUs,
        maxVUs: capacityMaxVUs,
      },
    ]),
  );
}

function buildScenarios() {
  if (TEST_MODE === "fixed") {
    return buildFixedScenarios();
  }
  if (TEST_MODE === "capacity") {
    return buildCapacityScenarios();
  }
  throw new Error(`unsupported TEST_MODE: ${TEST_MODE}`);
}

function buildBaseThresholds() {
  const thresholds = {
    "checks{phase:measure}": ["rate>=0.999"],
    "http_req_failed{phase:measure}": [`rate<${HTTP_REQ_FAILED_RATE}`],
    "order_coupon_missing_user_id{phase:measure}": ["count==0"],
    "order_coupon_unexpected_failure{phase:measure}": ["count==0"],
    "order_coupon_post_orders_duration{phase:measure}": [
      `p(99)<${ORDER_CREATE_P99_MS}`,
    ],
    "order_coupon_cancel_order_item_duration{phase:measure}": [
      `p(99)<${ORDER_CANCEL_P99_MS}`,
    ],
    "order_coupon_get_order_detail_duration{phase:measure}": [
      `p(99)<${ORDER_DETAIL_P99_MS}`,
    ],
    "order_coupon_get_order_detail_after_cancel_duration{phase:measure}": [
      `p(99)<${ORDER_DETAIL_P99_MS}`,
    ],
  };

  for (const type of ERROR_TYPES) {
    thresholds[`order_coupon_error_total{phase:measure,type:${type}}`] = [
      "count>=0",
    ];
  }

  for (const item of scenarioSchedules.filter(
    (schedule) => schedule.includeInThresholds,
  )) {
    thresholds[`dropped_iterations{scenario:${item.name}}`] = ["count==0"];
  }

  return thresholds;
}

function buildThresholds() {
  const thresholds = buildBaseThresholds();

  if (TEST_MODE !== "capacity") {
    return thresholds;
  }

  for (const [index, rate] of CAPACITY_RATES.entries()) {
    const name = capacityScenarioName(rate, index);
    thresholds[`http_req_failed{scenario:${name}}`] = [
      `rate<${HTTP_REQ_FAILED_RATE}`,
    ];
    thresholds[`order_coupon_post_orders_duration{scenario:${name}}`] = [
      `p(99)<${ORDER_CREATE_P99_MS}`,
    ];
    thresholds[`order_coupon_cancel_order_item_duration{scenario:${name}}`] = [
      `p(99)<${ORDER_CANCEL_P99_MS}`,
    ];
    thresholds[`order_coupon_get_order_detail_duration{scenario:${name}}`] = [
      `p(99)<${ORDER_DETAIL_P99_MS}`,
    ];
    thresholds[
      `order_coupon_get_order_detail_after_cancel_duration{scenario:${name}}`
    ] = [`p(99)<${ORDER_DETAIL_P99_MS}`];
  }

  return thresholds;
}

function buildScenarioIterationOffsets() {
  let offset = 0;
  const offsets = {};

  for (const schedule of scenarioSchedules) {
    offsets[schedule.name] = offset;
    offset += schedule.plannedIterations;
  }

  return offsets;
}

function findScenarioSchedule(name) {
  return scenarioSchedules.find((item) => item.name === name);
}

function currentSchedule() {
  return findScenarioSchedule(scenario.name);
}

function currentMetricTags(extra = {}) {
  const item = currentSchedule();
  return {
    phase: item?.phase || "measure",
    ...extra,
  };
}

function currentRequestTags(name) {
  return currentMetricTags({ name });
}

function isMeasurementIteration() {
  const item = currentSchedule();
  return item?.includeInThresholds !== false;
}

function addMeasureCounter(counter, value = 1, extraTags = {}) {
  if (isMeasurementIteration()) {
    counter.add(value, currentMetricTags(extraTags));
  }
}

function addError(type, extraTags = {}) {
  addMeasureCounter(orderCouponErrorTotal, 1, {
    type,
    ...extraTags,
  });
}

function runCheck(value, checks) {
  if (isMeasurementIteration()) {
    return check(value, checks, currentMetricTags());
  }

  return Object.values(checks).every((predicate) => predicate(value));
}

function logSchedule(startedAt) {
  const totalSeconds = scenarioSchedules.reduce(
    (maxEnd, item) =>
      Math.max(maxEnd, item.startOffsetSeconds + item.durationSeconds),
    0,
  );
  console.log(
    `[loadtest] planned total mode=${TEST_MODE} start="${formatTime(startedAt)}" end="${formatTime(addSeconds(startedAt, totalSeconds))}" planned_elapsed=${totalSeconds}s`,
  );

  for (const [index, item] of scenarioSchedules.entries()) {
    const stageStart = addSeconds(startedAt, item.startOffsetSeconds);
    const stageEnd = addSeconds(stageStart, item.durationSeconds);
    console.log(
      `[loadtest] planned stage=${index + 1}/${scenarioSchedules.length} scenario=${item.name} phase=${item.phase} rate=${item.rate}iter/s start="${formatTime(stageStart)}" end="${formatTime(stageEnd)}" rest_after=${item.restAfterSeconds}s planned_iterations=${item.plannedIterations}`,
    );
  }
}

function logScenarioStartIfNeeded() {
  if (scenario.iterationInTest !== 0) {
    return;
  }

  const item = currentSchedule();
  const rate = item ? item.rate : "unknown";
  console.log(
    `[loadtest] actual stage_start scenario=${scenario.name} phase=${item?.phase || "unknown"} rate=${rate}iter/s time="${formatTime(new Date())}"`,
  );
}

function logScenarioEndIfNeeded() {
  const item = currentSchedule();
  if (!item || scenario.iterationInTest !== item.plannedIterations - 1) {
    return;
  }

  console.log(
    `[loadtest] actual stage_last_iteration_end scenario=${scenario.name} phase=${item.phase} rate=${item.rate}iter/s time="${formatTime(new Date())}" planned_iterations=${item.plannedIterations}`,
  );
}

export function setup() {
  const startedAt = new Date();
  logSchedule(startedAt);
  return { startedAt: startedAt.toISOString() };
}

export function teardown(data) {
  const finishedAt = new Date();
  const startedAt = new Date(data.startedAt);
  console.log(
    `[loadtest] actual total_start="${formatTime(startedAt)}" total_end="${formatTime(finishedAt)}"`,
  );

  for (const [index, item] of scenarioSchedules.entries()) {
    const stageStart = addSeconds(startedAt, item.startOffsetSeconds);
    const stageEnd = addSeconds(stageStart, item.durationSeconds);
    console.log(
      `[loadtest] final stage=${index + 1}/${scenarioSchedules.length} scenario=${item.name} phase=${item.phase} rate=${item.rate}iter/s planned_start="${formatTime(stageStart)}" planned_end="${formatTime(stageEnd)}" rest_after=${item.restAfterSeconds}s`,
    );
  }
}

function pickCandidate() {
  const index =
    (scenarioIterationOffsets[scenario.name] || 0) + scenario.iterationInTest;

  if (index >= candidates.length) {
    fail(
      `not enough order coupon candidates: iteration=${index}, candidates=${candidates.length}. Increase candidate data or reduce the configured load.`,
    );
  }

  return candidates[index];
}

function parseJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function buildOrderReq(candidate) {
  const item = {
    cartItemId: candidate.cartItemId,
    orderQuantity: candidate.orderQuantity,
  };

  if (candidate.couponIssueId) {
    item.couponIssuedId = candidate.couponIssueId;
  }

  return { items: [item] };
}

function shouldCancel(candidate) {
  if (typeof candidate.cancelAfterOrder === "boolean") {
    return candidate.cancelAfterOrder;
  }

  if (CANCEL_RATIO <= 0) {
    return false;
  }

  return scenario.iterationInTest % 100 < Math.floor(CANCEL_RATIO * 100);
}

function isExpectedFailure(res) {
  const apiError = readApiError(res);
  return apiError && EXPECTED_FAILURE_CODES.has(apiError.code);
}

function loadtestAuthHeaders(candidate, headers = {}) {
  if (!candidate.userId) {
    addMeasureCounter(orderCouponMissingUserId);
    addError("missing_user_id");
    fail(`candidate missing userId: email=${candidate.email}`);
  }

  return {
    ...headers,
    "X-LoadTest-User-Id": String(candidate.userId),
    "X-LoadTest-Secret": LOADTEST_AUTH_SECRET,
  };
}

function recordLatency(metric, res) {
  if (isMeasurementIteration()) {
    metric.add(res.timings.duration, currentMetricTags());
  }
}

function getOrderDetail(orderId, candidate, metric, name = "GET /orders/{id}") {
  const res = http.get(`${BASE_URL}/orders/${orderId}`, {
    headers: loadtestAuthHeaders(candidate),
    tags: currentRequestTags(name),
  });
  recordLatency(metric, res);
  return { res, body: parseJson(res) };
}

function findTargetOrderItem(orderDetail, candidate) {
  const items = orderDetail?.itemList;
  if (!Array.isArray(items) || items.length === 0) {
    return null;
  }

  if (candidate.couponIssueId) {
    const couponItem = items.find(
      (item) =>
        Number(item?.usedCoupon?.couponIssuedId) === candidate.couponIssueId,
    );
    if (couponItem) {
      return couponItem;
    }
  }

  return (
    items.find(
      (item) => Number(item?.product?.productId) === candidate.productId,
    ) || items[0]
  );
}

function logCandidateFailure(label, candidate, extra = {}) {
  if (!FAILURE_DETAIL_LOGGING_ENABLED) {
    return;
  }

  console.error(
    JSON.stringify({
      label,
      iteration: scenario.iterationInTest,
      email: candidate.email,
      userId: candidate.userId,
      cartItemId: candidate.cartItemId,
      productId: candidate.productId,
      orderQuantity: candidate.orderQuantity,
      couponEventId: candidate.couponEventId,
      couponIssueId: candidate.couponIssueId,
      ...extra,
    }),
  );
}

function recordExpectedFailure(type, label, candidate, res, requestBody) {
  addMeasureCounter(orderCouponExpectedFailure);
  addError(type, {
    status: String(res.status),
    code: String(readApiError(res)?.code || "none"),
  });
  if (!FAILURE_DETAIL_LOGGING_ENABLED) {
    return;
  }

  const apiError = readApiError(res);
  console.log(
    JSON.stringify({
      label,
      iteration: scenario.iterationInTest,
      email: candidate.email,
      userId: candidate.userId,
      cartItemId: candidate.cartItemId,
      productId: candidate.productId,
      couponEventId: candidate.couponEventId,
      couponIssueId: candidate.couponIssueId,
      status: res.status,
      errorCode: apiError?.code,
      errorMessage: apiError?.message,
      requestBody,
    }),
  );
}

function recordUnexpectedFailure(type, label, candidate, res, requestBody) {
  addMeasureCounter(orderCouponUnexpectedFailure);
  addError(type, {
    status: String(res?.status || "none"),
    code: String(readApiError(res)?.code || "none"),
  });
  if (FAILURE_DETAIL_LOGGING_ENABLED) {
    logUnexpectedResponse(label, res, {
      email: candidate.email,
      userId: candidate.userId,
      cartItemId: candidate.cartItemId,
      couponIssueId: candidate.couponIssueId,
      iteration: scenario.iterationInTest,
    });
  }
  logCandidateFailure(label, candidate, {
    status: res?.status,
    requestBody,
    responseBody: res?.body,
  });
}

export default function () {
  logScenarioStartIfNeeded();

  try {
    runOrderCouponMixedFlow();
  } finally {
    logScenarioEndIfNeeded();
  }
}

function runOrderCouponMixedFlow() {
  const candidate = pickCandidate();

  const orderReq = buildOrderReq(candidate);
  const orderRes = http.post(`${BASE_URL}/orders`, JSON.stringify(orderReq), {
    headers: loadtestAuthHeaders(candidate, {
      "Content-Type": "application/json",
    }),
    tags: currentRequestTags("POST /orders"),
  });
  recordLatency(orderCouponPostOrdersDuration, orderRes);

  if (orderRes.status !== 200) {
    if (isExpectedFailure(orderRes)) {
      recordExpectedFailure(
        "expected_order_failure",
        "expected order coupon failure",
        candidate,
        orderRes,
        orderReq,
      );
      return;
    }
    recordUnexpectedFailure(
      "unexpected_order_failure",
      "unexpected order coupon failure",
      candidate,
      orderRes,
      orderReq,
    );
    return;
  }

  const orderId = Number(orderRes.body);
  const orderOk = runCheck(orderRes, {
    "order coupon create status is 200": (r) => r.status === 200,
    "order coupon create returns order id": () => Number.isInteger(orderId),
  });
  if (!orderOk) {
    recordUnexpectedFailure(
      "invalid_order_response",
      "order coupon response invalid",
      candidate,
      orderRes,
      orderReq,
    );
    return;
  }

  const detail = getOrderDetail(
    orderId,
    candidate,
    orderCouponGetOrderDetailDuration,
  );
  const targetOrderItem = findTargetOrderItem(detail.body, candidate);
  const detailOk = runCheck(detail.res, {
    "order detail status is 200": (r) => r.status === 200,
    "order detail has target item": () => !!targetOrderItem,
    "coupon order has used coupon": () =>
      !candidate.couponIssueId ||
      Number(targetOrderItem?.usedCoupon?.couponIssuedId) ===
        candidate.couponIssueId,
  });
  if (!detailOk) {
    recordUnexpectedFailure(
      "invalid_detail_response",
      "order coupon detail invalid",
      candidate,
      detail.res,
      orderReq,
    );
    return;
  }

  addMeasureCounter(orderCouponOrderSuccess);

  if (!shouldCancel(candidate)) {
    return;
  }

  const cancelRes = http.patch(
    `${BASE_URL}/order-items/${targetOrderItem.orderItemId}/cancel`,
    null,
    {
      headers: loadtestAuthHeaders(candidate),
      tags: currentRequestTags("PATCH /order-items/{id}/cancel"),
    },
  );
  recordLatency(orderCouponCancelOrderItemDuration, cancelRes);

  if (cancelRes.status !== 200) {
    if (isExpectedFailure(cancelRes)) {
      recordExpectedFailure(
        "expected_cancel_failure",
        "expected order coupon cancel failure",
        candidate,
        cancelRes,
        {
          orderId,
          orderItemId: targetOrderItem.orderItemId,
        },
      );
      return;
    }
    recordUnexpectedFailure(
      "unexpected_cancel_failure",
      "unexpected order coupon cancel failure",
      candidate,
      cancelRes,
      {
        orderId,
        orderItemId: targetOrderItem.orderItemId,
      },
    );
    return;
  }

  const cancelOk = runCheck(cancelRes, {
    "order coupon cancel status is 200": (r) => r.status === 200,
  });
  if (!cancelOk) {
    recordUnexpectedFailure(
      "invalid_cancel_response",
      "order coupon cancel response invalid",
      candidate,
      cancelRes,
      {
        orderId,
        orderItemId: targetOrderItem.orderItemId,
      },
    );
    return;
  }

  const afterCancel = getOrderDetail(
    orderId,
    candidate,
    orderCouponGetOrderDetailAfterCancelDuration,
    "GET /orders/{id} after cancel",
  );
  const canceledOrderItem = findTargetOrderItem(afterCancel.body, candidate);
  const afterCancelOk = runCheck(afterCancel.res, {
    "order detail after cancel status is 200": (r) => r.status === 200,
    "order item status is canceled": () =>
      canceledOrderItem?.status === "CANCELED",
  });
  if (!afterCancelOk) {
    recordUnexpectedFailure(
      "invalid_cancel_detail_response",
      "order coupon cancel detail invalid",
      candidate,
      afterCancel.res,
      {
        orderId,
        orderItemId: targetOrderItem.orderItemId,
      },
    );
    return;
  }

  addMeasureCounter(orderCouponCancelSuccess);
}
