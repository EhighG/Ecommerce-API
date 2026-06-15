#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# 기본 경로
# ============================================================
BASE_DIR="/opt/ecommerce"
LOADTEST_DIR="$BASE_DIR/loadtest"
RESULTS_ROOT="$BASE_DIR/results/perf"
REPO_DIR="$BASE_DIR/repo/Ecommerce-project"

ENV_FILE="$LOADTEST_DIR/.env"
SQL_TEMPLATE="$REPO_DIR/infra/sql/order_coupon_mixed_candidates_V3.sql"
K6_SCRIPT="$REPO_DIR/k6/260519_order_bypass_auth/order-coupon-mixed.js"
CANDIDATE_FILE="$REPO_DIR/k6/data/order_coupon_mixed_candidates.csv"

# ============================================================
# 실행 기본값
# ============================================================
RUN_ID="${RUN_ID:-order-coupon-mixed-$(date +%Y%m%d-%H%M%S)}"
RESULT_DIR="$RESULTS_ROOT/$RUN_ID"

# fixed mode
TEST_MODE="${TEST_MODE:-fixed}"
ORDER_RATE="${ORDER_RATE:-60}"
DURATION="${DURATION:-3m}"
WARMUP_ENABLED="${WARMUP_ENABLED:-true}"
WARMUP_RATE="${WARMUP_RATE:-}"
WARMUP_DURATION="${WARMUP_DURATION:-3m}"
REST_DURATION="${REST_DURATION:-30s}"
PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS:-300}"
MAX_VUS="${MAX_VUS:-800}"

# capacity mode
CAPACITY_RATES="${CAPACITY_RATES:-10,20,30,40,50,60}"
CAPACITY_STAGE_DURATION="${CAPACITY_STAGE_DURATION:-3m}"
CAPACITY_REST_DURATION="${CAPACITY_REST_DURATION:-$REST_DURATION}"
CAPACITY_GRACEFUL_STOP="${CAPACITY_GRACEFUL_STOP:-30s}"
CAPACITY_PRE_ALLOCATED_VUS="${CAPACITY_PRE_ALLOCATED_VUS:-$PRE_ALLOCATED_VUS}"
CAPACITY_MAX_VUS="${CAPACITY_MAX_VUS:-$MAX_VUS}"

# thresholds / flow options
CANCEL_RATIO="${CANCEL_RATIO:-0.3}"
ORDER_CREATE_P99_MS="${ORDER_CREATE_P99_MS:-1500}"
ORDER_CANCEL_P99_MS="${ORDER_CANCEL_P99_MS:-1500}"
ORDER_DETAIL_P99_MS="${ORDER_DETAIL_P99_MS:-500}"
HTTP_REQ_FAILED_RATE="${HTTP_REQ_FAILED_RATE:-0.001}"
FAILURE_DETAIL_LOGGING_ENABLED="${FAILURE_DETAIL_LOGGING_ENABLED:-false}"

# SQL script params
SQL_PARAM1="${SQL_PARAM1:-1}"

REST_AFTER_DATA_PREPARE_SECONDS="${REST_AFTER_DATA_PREPARE_SECONDS:-30}"

# ============================================================
# /opt/ecommerce 권한 보정
# ============================================================
sudo -v

sudo mkdir -p "$LOADTEST_DIR" "$RESULTS_ROOT" "$RESULT_DIR"
sudo chown -R "$USER:$USER" "$LOADTEST_DIR" "$RESULTS_ROOT"

# ============================================================
# 환경변수 로드
# ============================================================
if [[ ! -f "$ENV_FILE" ]]; then
  echo "[perf] env file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

: "${RDB_HOST:?RDB_HOST is required}"
: "${RDB_PORT:=3306}"
: "${RDB_NAME:?RDB_NAME is required}"
: "${RDB_USER:?RDB_USER is required}"
: "${RDB_PASSWORD:?RDB_PASSWORD is required}"
: "${BASE_URL:?BASE_URL is required}"
: "${LOADTEST_AUTH_SECRET:?LOADTEST_AUTH_SECRET is required}"

# ============================================================
# 파일 존재 확인
# ============================================================
if [[ ! -d "$REPO_DIR" ]]; then
  echo "[perf] repo dir not found: $REPO_DIR" >&2
  exit 1
fi

if [[ ! -f "$SQL_TEMPLATE" ]]; then
  echo "[perf] SQL template not found: $SQL_TEMPLATE" >&2
  exit 1
fi

if [[ ! -f "$K6_SCRIPT" ]]; then
  echo "[perf] k6 script not found: $K6_SCRIPT" >&2
  exit 1
fi

mkdir -p "$(dirname "$CANDIDATE_FILE")"

# ============================================================
# 실행 정보 기록
# ============================================================
cd "$REPO_DIR"

{
  echo "[perf] run_id=$RUN_ID"
  echo "[perf] started_at=$(date --iso-8601=seconds)"
  echo "[perf] base_dir=$BASE_DIR"
  echo "[perf] loadtest_dir=$LOADTEST_DIR"
  echo "[perf] repo_dir=$REPO_DIR"
  echo "[perf] result_dir=$RESULT_DIR"
  echo "[perf] sql_template=$SQL_TEMPLATE"
  echo "[perf] candidate_file=$CANDIDATE_FILE"
  echo "[perf] base_url=$BASE_URL"
  echo "[perf] rdb=$RDB_HOST:$RDB_PORT/$RDB_NAME"

  echo "[perf] test_mode=$TEST_MODE"
  echo "[perf] order_rate=$ORDER_RATE"
  echo "[perf] duration=$DURATION"
  echo "[perf] warmup_enabled=$WARMUP_ENABLED"
  echo "[perf] warmup_rate=${WARMUP_RATE:-auto}"
  echo "[perf] warmup_duration=$WARMUP_DURATION"
  echo "[perf] rest_duration=$REST_DURATION"
  echo "[perf] pre_allocated_vus=$PRE_ALLOCATED_VUS"
  echo "[perf] max_vus=$MAX_VUS"

  echo "[perf] capacity_rates=$CAPACITY_RATES"
  echo "[perf] capacity_stage_duration=$CAPACITY_STAGE_DURATION"
  echo "[perf] capacity_rest_duration=$CAPACITY_REST_DURATION"
  echo "[perf] capacity_graceful_stop=$CAPACITY_GRACEFUL_STOP"
  echo "[perf] capacity_pre_allocated_vus=$CAPACITY_PRE_ALLOCATED_VUS"
  echo "[perf] capacity_max_vus=$CAPACITY_MAX_VUS"

  echo "[perf] cancel_ratio=$CANCEL_RATIO"
  echo "[perf] order_create_p99_ms=$ORDER_CREATE_P99_MS"
  echo "[perf] order_cancel_p99_ms=$ORDER_CANCEL_P99_MS"
  echo "[perf] order_detail_p99_ms=$ORDER_DETAIL_P99_MS"
  echo "[perf] http_req_failed_rate=$HTTP_REQ_FAILED_RATE"
  echo "[perf] failure_detail_logging_enabled=$FAILURE_DETAIL_LOGGING_ENABLED"

  echo "[perf] sql_param1=$SQL_PARAM1"
} | tee "$RESULT_DIR/run-info.txt"

# ============================================================
# MySQL 접속 확인
# ============================================================
echo "[perf] check mysql"
mysql \
  -h "$RDB_HOST" \
  -P "$RDB_PORT" \
  -u "$RDB_USER" \
  -p"$RDB_PASSWORD" \
  "$RDB_NAME" \
  -e "select 1" \
  | tee "$RESULT_DIR/mysql-check.txt"

# ============================================================
# SQL param 치환 후 데이터 세팅 + candidate 추출
# ============================================================
TMP_SQL="$(mktemp)"
trap 'rm -f "$TMP_SQL"' EXIT

sed \
  -e "s/:couponEventId/${SQL_PARAM1}/g" \
  "$SQL_TEMPLATE" > "$TMP_SQL"

LEFTOVER_PARAMS="$(grep -nE ':[A-Za-z_][A-Za-z0-9_]*' "$TMP_SQL" || true)"
if [[ -n "$LEFTOVER_PARAMS" ]]; then
  echo "[perf] unresolved SQL params remain:" >&2
  echo "$LEFTOVER_PARAMS" >&2
  exit 1
fi

echo "[perf] prepared SQL path=$TMP_SQL"
echo "[perf] execute data setup and export"

mysql \
  -h "$RDB_HOST" \
  -P "$RDB_PORT" \
  -u "$RDB_USER" \
  -p"$RDB_PASSWORD" \
  --batch \
  --raw \
  "$RDB_NAME" \
  < "$TMP_SQL" \
  > "$CANDIDATE_FILE"

# ============================================================
# candidate 파일 검증
# ============================================================
echo "[perf] candidate preview"
head -5 "$CANDIDATE_FILE" | tee "$RESULT_DIR/candidate-preview.txt"

echo "[perf] candidate count"
wc -l "$CANDIDATE_FILE" | tee "$RESULT_DIR/candidate-count.txt"

if [[ ! -s "$CANDIDATE_FILE" ]]; then
  echo "[perf] candidate file is empty: $CANDIDATE_FILE" >&2
  exit 1
fi

HEADER="$(head -1 "$CANDIDATE_FILE")"
echo "[perf] candidate header=$HEADER" | tee "$RESULT_DIR/candidate-header.txt"

if ! echo "$HEADER" | grep -q "email"; then
  echo "[perf] candidate header does not contain email. SQL output may be invalid." >&2
  exit 1
fi

if ! echo "$HEADER" | grep -q "cartItemId"; then
  echo "[perf] candidate header does not contain cartItemId. SQL output may be invalid." >&2
  exit 1
fi

# ============================================================
# DB 세팅 직후 rest
# ============================================================
echo "[perf] rest ${REST_AFTER_DATA_PREPARE_SECONDS}s"
sleep "$REST_AFTER_DATA_PREPARE_SECONDS"

# ============================================================
# k6 실행
# ============================================================
echo "[perf] k6 run"
ulimit -n 65535 || true

K6_ENV=(
  "BASE_URL=$BASE_URL"
  "LOADTEST_AUTH_SECRET=$LOADTEST_AUTH_SECRET"
  "ORDER_COUPON_CANDIDATES_FILE=../data/order_coupon_mixed_candidates.csv"

  "CANCEL_RATIO=$CANCEL_RATIO"
  "TEST_MODE=$TEST_MODE"
  "ORDER_CREATE_P99_MS=$ORDER_CREATE_P99_MS"
  "ORDER_CANCEL_P99_MS=$ORDER_CANCEL_P99_MS"
  "ORDER_DETAIL_P99_MS=$ORDER_DETAIL_P99_MS"
  "HTTP_REQ_FAILED_RATE=$HTTP_REQ_FAILED_RATE"

  "ORDER_RATE=$ORDER_RATE"
  "DURATION=$DURATION"
  "WARMUP_ENABLED=$WARMUP_ENABLED"
  "WARMUP_DURATION=$WARMUP_DURATION"
  "REST_DURATION=$REST_DURATION"
  "FAILURE_DETAIL_LOGGING_ENABLED=$FAILURE_DETAIL_LOGGING_ENABLED"
  "PRE_ALLOCATED_VUS=$PRE_ALLOCATED_VUS"
  "MAX_VUS=$MAX_VUS"

  "CAPACITY_RATES=$CAPACITY_RATES"
  "CAPACITY_STAGE_DURATION=$CAPACITY_STAGE_DURATION"
  "CAPACITY_REST_DURATION=$CAPACITY_REST_DURATION"
  "CAPACITY_GRACEFUL_STOP=$CAPACITY_GRACEFUL_STOP"
  "CAPACITY_PRE_ALLOCATED_VUS=$CAPACITY_PRE_ALLOCATED_VUS"
  "CAPACITY_MAX_VUS=$CAPACITY_MAX_VUS"
)

if [[ -n "${WARMUP_RATE:-}" ]]; then
  K6_ENV+=("WARMUP_RATE=$WARMUP_RATE")
fi

env "${K6_ENV[@]}" \
  k6 run \
  --summary-export "$RESULT_DIR/k6-summary.json" \
  "$K6_SCRIPT" \
  2>&1 | tee "$RESULT_DIR/k6-output.txt"

echo "[perf] ended_at=$(date --iso-8601=seconds)" | tee "$RESULT_DIR/end-time.txt"