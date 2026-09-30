# k6 — 부하테스트 스크립트

## 담당 범위
- 담당하는 것: 실제 구매 흐름을 흉내 낸 부하·동시성 시나리오, 데이터 준비 스크립트, 공용 라이브러리.
  - 주문·쿠폰 혼합 부하: `260519_order_bypass_auth/order-coupon-mixed.js`(헤더 우회 인증, fixed·capacity 모드). `../infra/scripts`의 자동 실행 스크립트가 이 파일을 돌린다.
  - 동시성 검증: `coupon/coupon-issue-concurrency.js`(선착순 소진), `coupon/coupon-issue-duplicate.js`(1인 1장), `order/order-stock-concurrency.js`(초과 판매 없음)
  - 상품 검색 부하: `product_search/measure-product-search.js`
  - 데이터 준비(이 순서로 돌린다): `join-users.js`(가입) → `register-products.js` 또는 `product_search/register-products-csv.js`(상품) → `add-cart-items.js`(장바구니) → `order/create-orders-from-cart.js`(주문) → `order/process-ordered-to-confirmed.js`(배송·구매확정) → `write-reviews.js`(리뷰)
  - 공용 라이브러리 `lib/`: `.env` 로딩(`env.js`), CSRF·로그인·오류 본문 파싱(`auth.js`), 멱등 키(`idempotency.js`), 후보 CSV 파싱(`candidates.js`, `coupon-candidates.js`)
- 담당하지 않는 것: 애플리케이션 코드, 주문·쿠폰 후보 SQL(`../infra/sql`), VM 자동 실행 스크립트(`../infra/scripts`), 결과 분석 문서
- `data/`(계정 목록, 후보 CSV, mock 데이터), `.env`, `test-result/`는 커밋하지 않는다. `.env`의 키는 `.env.example`에 있다.
- 데이터 파일과 그것을 만드는 SQL은 로컬에만 있어서 저장소만으로는 데이터를 재현할 수 없다. 빠진 것은 `docs/tracking/findings/ops-k6.md`에 있다.

## 항상 지켜야 할 것
- 필수 값은 `lib/env.js`의 `env(name)`으로 읽는다. 읽는 순서는 `__ENV` → `.env`(`K6_ENV_FILE`, 또는 관례 경로 순서대로)이고, 없으면 즉시 실패한다. `BASE_URL`은 `/api`까지 포함한다(예: `http://host:8081/api`).
- 로그인 방식 스크립트는 `PASSWORD`가 필요하다. 모든 테스트 계정이 이 비밀번호 하나를 같이 쓰고, 새로 가입시킬 계정의 비밀번호는 가입 비밀번호 규칙(`docs/security.md`)을 만족해야 한다. 실제 값은 어디에도 적지 않는다.
- 인증 방식은 두 가지다.
  - **로그인 방식**(`lib/auth.js`): `GET /auth/csrf` → 로그인 → 상태 변경 요청에 CSRF 헤더. 403이고 본문에 `code`가 없을 때만(CSRF 실패) 토큰을 다시 받아 한 번 재시도한다(`requestWithCsrfRetry`). `code`가 있는 403은 업무 오류라 재시도하지 않는다.
  - **헤더 우회 방식**(`260519_order_bypass_auth`): `X-LoadTest-User-Id`, `X-LoadTest-Secret`. 대상 서버가 `loadtest` 프로필이고 우회가 켜져 있어야 한다. 실사용 서버를 대상으로 쓰지 않는다.
- 주문 생성 요청에는 주문마다 새 `Idempotency-Key`를 넣고(`lib/idempotency.js`), 같은 주문을 재시도할 때는 같은 키를 쓴다. 키를 재사용하면 서버가 기존 주문을 재응답하므로 처리량이 부풀려진다.
- 응답을 파싱하는 코드는 서버 계약(`docs/contracts.md`)을 따른다. 오류 `code`는 **문자열**이다(`"2501"`). 스크립트의 기대 코드 상수도 문자열로 쓴다.

## 알아둘 구현 방식
- 주문·쿠폰 혼합 시나리오(260519)
  - 기본은 `TEST_MODE=fixed`다. 워밍업(기본 켜짐, 3분, 목표율의 절반) → 휴식(30초) → 측정 구간(`ORDER_RATE` 기본 30, `DURATION` 기본 3m). `TEST_MODE=capacity`면 `CAPACITY_RATES`의 부하율을 단계별로 돈다.
  - 임계값 기본값: 주문 생성·취소 p99 1500ms, 상세 p99 500ms, 실패율 0.1%. 환경변수로 덮어쓸 수 있다.
  - `CANCEL_RATIO`(기본 0.3) 비율로 주문항목을 취소하고 다시 조회한다. 취소 여부는 이 환경변수만 정한다.
  - 후보 CSV(`data/order_coupon_mixed_candidates.csv`)는 주문 가능한 (사용자, 장바구니 항목, 쿠폰) 조합이다. 열은 `email, userId, cartItemId, productId, orderQuantity, couponEventId, couponIssuedId`다. 반복마다 다른 행을 써야 재고와 쿠폰이 겹치지 않는다. 테스트 전에 `../infra/sql`의 후보 SQL로 다시 만든다.
- 동시성 스크립트
  - 쿠폰 발급: 원하는 수량으로 새 이벤트를 만들고(`POST /coupons/events`) 열린 뒤 `COUPON_EVENT_ID`로 돌린다. 이미 Redis에 올라간 이벤트는 DB의 수량을 바꿔도 Redis 재고가 바뀌지 않는다.
  - 재고: 후보 CSV(`data/order_stock_concurrency_candidates.csv`)가 필요하다.
- 상품 검색 스크립트의 기준 데이터셋은 `product_search/register-products-csv.js`로 넣은 `data/product_name_description_sample.csv`(2만 건)다. `register-products.js`의 mock 데이터로는 키워드 대부분이 검색되지 않는다.
- 데이터 준비 스크립트는 다시 돌려도 된다. 가입은 이미 있는 이메일(409 `1001`)을, 리뷰는 이미 쓴 리뷰(`6002`)와 삭제된 상품(`2000`)을 건너뛴다.
- 상품 등록 스크립트는 카테고리 ID 10~1009가 있다고 가정한다.

## 테스트 기준
스크립트를 바꾸면 아래를 확인한다.
- `k6 inspect <스크립트>`로 초기화(데이터 파일 열기, 옵션 계산)가 되는지 본다. 요청은 보내지 않는다.
- 소량으로 로컬 서버에 먼저 돌려 본다. 260519는 `WARMUP_ENABLED=false`와 낮은 `ORDER_RATE`, `DURATION=30s`로 준다(워밍업이 켜져 있으면 약 4분 돈다). 동시성·데이터 준비 스크립트는 `VUS`와 반복 수를 낮춘다. 체크 실패율이 0인지 확인한다.
- 결과를 비교할 때는 워밍업 유무, 부하율, 구간, 데이터, 서버·DB 사양이 같은지 확인한다.
