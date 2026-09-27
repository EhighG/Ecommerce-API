# 외부 인터페이스 계약 (REST API)

소비자는 HTTP 클라이언트(웹 프론트엔드, k6 스크립트 등)다. 기본 주소는 `http(s)://{호스트}:8081/api`이고, 아래 경로에는 `/api`를 생략한다. 요청과 응답 본문은 모두 JSON(UTF-8)이다.

## 공통 규칙

### 인증과 CSRF
1. `GET /auth/csrf`를 호출해 세션 쿠키(이름은 `SESSION`. 클라이언트는 이름에 의존하지 말고 쿠키 저장소로 그대로 돌려보낸다)와 `{headerName, paramName, token}`을 받는다.
2. 이후 모든 요청에 이 쿠키를 보낸다.
3. `POST`, `PATCH`, `DELETE`에는 `{headerName}: {token}` 헤더를 넣는다. `headerName`은 보통 `X-CSRF-TOKEN`이다.
4. `POST /auth/login`으로 로그인하면 같은 세션이 인증된 상태가 된다.

- 세션이 만료되거나 로그아웃한 뒤에는 1번부터 다시 한다.
- 상태 변경 요청이 403이고 본문에 `code`가 없으면 CSRF 실패일 수 있다. 토큰을 다시 받아 한 번 재시도한다.
- 세션은 30분 동안 요청이 없으면 만료된다.

### 성공 응답
- 생성 API는 `200 OK`와 함께 새 자원의 ID(JSON 숫자, 예: `1`)를 본문으로 돌려준다. `201`은 쓰지 않는다.
- 상태 변경 API(수정, 삭제, 전이)는 `200 OK`이고 본문이 없다.
- 시각은 ISO-8601 UTC 문자열(`"2026-07-14T10:00:00Z"`)이다. 날짜만 있는 값은 `"2026-07-14"`다.
- 금액은 원 단위 정수다.

### 오류 응답
애플리케이션 오류는 모두 아래 형식이다. **`code`는 4자리 문자열**이다.

```json
{ "code": "7507", "message": "쿠폰이 모두 소진되었습니다." }
```

- `message`는 사용자에게 그대로 보여줄 수 있는 한국어 문장이다. 같은 `code`라도 상황에 따라 더 구체적인 문장이 올 수 있다.
- 클라이언트의 분기 처리는 **HTTP 상태와 `code`로만** 한다. `message` 문자열로 분기하지 않는다.

| 상황 | 상태 | 본문 |
|---|---|---|
| 로그인 필요(세션 없음/만료), 로그인 실패 | 401 | 없음 |
| 역할 부족, CSRF 실패 | 403 | `code` 없음 |
| 부하테스트 헤더 인증 실패 | 401 | `code` 없음 |
| 본문 필드 검증 실패 (`@NotNull` 등) | 400 | `9001`, 첫 번째 위반 필드의 한국어 메시지 |
| 본문이 JSON이 아니거나 타입이 틀림, **본문 필드 사이 조건 위반**(아래 참고) | 400 | `9001`, `"요청 Body 읽기 실패"` |
| 쿼리 파라미터 enum 값 오류 | 400 | `9001`, 프레임워크 영문 메시지 |
| 경로 변수 타입 오류 | 400 | `9001`, `"요청 인자 타입 오류"` |
| 필수 쿼리 파라미터 누락 | 400 | `9001`, `"필수 요청 파라미터 누락"` |
| 주문항목 동시 상태 변경 충돌 | 409 | `3004` |
| 처리하지 못한 서버 오류 | 500 | `9999`, `"잠시 후 다시 시도해주세요."` |

**현재 동작의 예외 두 가지(수정 예정인 결함이다. 클라이언트는 이 동작을 전제로 로직을 짜지 않는다)**
- 본문 필드 사이의 조건 위반은 전용 코드 대신 `9001 "요청 Body 읽기 실패"`로 응답한다. 해당 조건: 가입 시 비밀번호 확인 불일치, 가입 역할 `ADMIN`, 상품 수정에서 두 필드 모두 누락이나 공백 설명.
- **쿼리 파라미터 사이의 조건 위반은 500(`9999`)으로 응답한다.** 해당 조건:
  - 상품 목록의 `size`가 20/50/100이 아님
  - 주문항목 목록의 `orderId`/`sellerId` 조합 오류, `size` 오류, 구매자 조회에서 `statusList` 사용
  - 리뷰 목록의 `searchBy`에 맞는 ID 누락

### 페이지 응답
```json
{ "items": [ ... ], "page": 0, "size": 20, "totalCount": 1, "totalPages": 1, "hasNext": false }
```
- 리뷰 목록은 `items` 대신 `productReviews`나 `userReviews`에 목록이 담긴다.
- `page`는 0부터 시작한다.

### 공통 객체
- `UserSummary`: `{ "id": 2, "nickname": "seller" }`
- `ProductListItem`(상품 목록, 장바구니):
  `{ "id", "name", "categoryName", "unitPrice", "thumbnailImageUrl"(없으면 null), "seller": UserSummary, "inventoryQuantity", "avgRating"(0.0~5.0 실수) }`
- `UsedCoupon`: `{ "couponIssuedId", "name", "type": "FIXED_AMOUNT"|"PERCENT", "discountValue", "maxDiscountAmount", "discountAmount", "usedAt" }`
- 주문항목 상태: `ORDERED`, `SHIPPED`, `DELIVERED`, `PURCHASE_CONFIRMED`, `CANCELED`

권한 표기: `PUBLIC`(누구나), `AUTH`(로그인), `BUYER`, `SELLER`, `ADMIN`.

## Auth

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `GET /auth/csrf` | PUBLIC | — | `{ "headerName", "paramName", "token" }` | — |
| `POST /auth/login` | PUBLIC | `{ "email", "password" }` | 200, 본문 없음, 세션 인증 | 401(자격 증명 오류, 탈퇴, 형식 오류), 403(CSRF) |
| `POST /auth/logout` | PUBLIC | — | 200, 세션 무효화, 쿠키 삭제 | 403(CSRF) |

## User

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `POST /users` | PUBLIC | `{ "email"(이메일 형식, 30자 이하. 초과하면 현재 500), "nickname"(≤20), "password", "passwordCheck", "role": "BUYER"|"SELLER" }` | 사용자 ID | 400(검증), 409 `1001`(이메일 중복) |
| `GET /users/{userId}` | AUTH | — | 프로필(아래) | 404 `1000`(없음, 탈퇴) |
| `GET /users/me` | AUTH | — | 프로필 | 404 `1000` |
| `GET /users` | ADMIN | — | `[{ "id", "nickname", "email", "role", "deleted" }]` (전체, 탈퇴자 포함, ID순) | 403 |
| `PATCH /users/me` | AUTH | `{ "nickname" }`(공백 불가, 20자 이하) | 200 | 400, 404 `1000`. 20자 초과는 현재 검증이 없어 500 |
| `PATCH /users/me/password` | AUTH | `{ "oldPassword", "newPassword" }` | 200, **현재 세션 로그아웃** | 401 `1003`(기존 비밀번호 불일치), 404 `1000` |
| `POST /users/me/withdraw` | AUTH | `{ "password" }` | 200, 현재 세션 로그아웃 | 401 `1003`, 403 `1004`(판매자에게 진행 중 주문 있음), 404 `1000` |

프로필 형식:
```json
{ "userId": 1, "email": "…", "nickname": "…", "role": "BUYER", "joinDate": "2026-07-14",
  "reviewSection": { "totalCount": 3, "reviewList": [ UserReview … 최근 5개 ] } }
```
- `reviewSection`은 구매자일 때만 들어가고, 아니면 필드 자체가 없다.
- `joinDate`는 서울 기준 날짜다.
- `UserReview` 형식: `{ "reviewId", "product": { "productId", "name", "thumbnailImageUrl" }, "halfStars", "content", "lastUpdatedAt", "isUpdated" }`

## Product

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `POST /products` | SELLER | `{ "name"(50자 이하. 초과하면 현재 500), "categoryId", "description"(≤1000), "unitPrice"(≥0), "initialInventory"(≥0), "imageIdList": [{ "imageId", "order"(≥1) }] }` (`imageIdList`는 생략하면 빈 목록) | 상품 ID | 400 `2001`(카테고리 없음), 400 `8003`(이미지 없음), 400 `8002`(내 이미지 아님), 400 `8004`(이미 사용 중), 404 `1000` |
| `GET /products` | PUBLIC | 쿼리: `keyword`, `categoryId`, `sellerId`, `page`(기본 0), `size`(20/50/100, 기본 20), `sortBy`(`ORDER_COUNT`/`RATING`/`VIEW_COUNT`/`PRICE`/`REG_DATE`, 기본 `REG_DATE`), `direction`(`ASC`/`DESC`, 기본 `DESC`) | 페이지(`items`: ProductListItem) | 400 `9001`, `size` 오류는 현재 500 |
| `GET /products/{productId}` | PUBLIC | — | `{ "id", "name", "category": {"id","name"}, "unitPrice", "description", "imageUrls": [표시 순서대로], "seller": UserSummary, "inventoryQuantity", "avgRating" }` (조회수 +1) | 404 `2000`(없음, 삭제) |
| `PATCH /products/{productId}` | SELLER | `{ "description"?, "unitPrice"? }` (하나 이상) | 200 | 400, 403 `2002`(내 상품 아님), 404 `2000` |
| `PATCH /products/{productId}/images` | SELLER | `{ "imageIdList": [...] }` (`null`/빈 목록이면 전체 제거) | 200 | 등록 API의 이미지 오류, 403 `2002`, 404 `2000` |
| `DELETE /products/{productId}` | SELLER | — | 200 | 403 `2002`, 404 `2000` |
| `POST /products/category` | ADMIN | `{ "name"(≤15) }` | 카테고리 ID | 400, 409 `2005`(중복) |
| `GET /products/category` | PUBLIC | — | `[{ "id", "name" }]` (ID순) | — |
| `DELETE /products/category/{categoryId}` | ADMIN | — | 200 | 400 `2001`. 상품이 걸려 있으면 현재 500 |
| `PATCH /products/inventory` | SELLER | `{ "productId", "quantity"(≥0, 바뀐 뒤 수량) }` | 200 | 400 `2004`(삭제된 상품), 403 `2002`, 500 `2500`(재고 행 없음) |

- 썸네일은 이미지 `order`가 1인 것이다.
- 존재하지 않는 `productId`로 재고를 수정하면 `2500`(500)이 된다.

## Media

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `POST /media/upload-url` | SELLER | `{ "originalFileName", "contentType"("image/…") }` | `{ "objectKey", "uploadUrl", "expiresAt" }` | 400 `8001`(이미지 형식 아님) |
| (GCS 직접 업로드) | — | `PUT {uploadUrl}`, 헤더 `Content-Type`은 발급 요청과 같은 값 | GCS 응답 | URL 만료(10분), 헤더 불일치 시 GCS 403 |
| `POST /media/upload-complete` | SELLER | `{ "objectKey" }` | `{ "imageId", "objectKey", "imageUrl" }` (같은 사람이 다시 호출해도 같은 결과) | 404 `8000`(GCS에 객체 없음), 400 `8001`, 400 `8002`(다른 사람이 등록) |
| `GET /media/uploaded-images` | ADMIN | — | `[{ "id", "objectKey", "uploadUserId", "contentType", "fileSize", "attached" }]` | 403 |

## Cart

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `POST /cart-items` | BUYER | `{ "productId", "quantity"(≥1) }` (이미 있으면 수량 합산) | 장바구니 항목 ID | 400, 404 `1000`, 404 `2000`(없음, 삭제된 상품), 409 `7001`(동시 추가 충돌, 재시도) |
| `GET /cart-items` | BUYER | — | `[{ "id", "product": ProductListItem, "quantity", "linePrice" }]` (최근 추가순) | — |
| `PATCH /cart-items/quantity` | BUYER | `{ "productId", "quantity"(≥1, 바뀐 뒤 수량) }` | 200 | 400, 404 `7000` |
| `DELETE /cart-items/{cartItemId}` | BUYER | — | 200 | 404 `7000`(없음, 남의 항목) |

## Order

### `POST /orders` (BUYER)
- 본문 검증이 멱등 키 검증보다 먼저 실행된다. 본문이 잘못됐으면 키가 없어도 `9001`이 먼저 나간다.
- 헤더 `Idempotency-Key`: 필수, 1~128자, `[A-Za-z0-9._:-]`만 허용. 사용자가 누른 "주문하기" 한 번마다 새 값을 만들고, **재시도할 때는 같은 값을 쓴다**.
- 본문:
  ```json
  { "items": [ { "cartItemId": 1, "orderQuantity": 2, "couponIssuedId": 10 } ] }
  ```
  - `items`는 1개 이상이다.
  - `orderQuantity`는 1 이상이고, 장바구니 수량보다 커도 된다.
  - `couponIssuedId`는 선택이다.
- 출력: 주문 ID. 같은 키로 같은 내용을 다시 보내면 새로 만들지 않고 같은 ID를 돌려준다(24시간).

| 오류 | 상태·코드 |
|---|---|
| 키 없음 / 형식 오류 | 400 `9100` / 400 `9101` |
| 같은 키, 다른 내용 | 409 `9102` |
| 같은 키 요청이 처리 중 | 409 `9103` (잠시 후 같은 키로 재시도) |
| 한 주문에 같은 장바구니 항목 두 번 | 400 `9001` |
| 장바구니 항목 없음, 남의 항목 | 404 `7000` |
| 삭제된 상품 포함 | 400 `2004` |
| 같은 쿠폰 두 번 / 쿠폰 없음, 남의 쿠폰 / 쓸 수 없는 쿠폰(사용됨, 만료) | 400 `7509` / 400 `7510` / 400 `7501` |
| 할인액이 0원이 되는 쿠폰 적용 | 400 `9001` (쿠폰 전용 코드는 아직 없음) |
| 재고 부족 | 400 `2501` |
| 사용자 없음 | 404 `1000` |
| 재고 행 없음 / 통계 행 없음 | 400 `2501`(재고 부족과 구분되지 않음) / 500 `2006` |

실패한 요청은 아무것도 반영되지 않는다. 같은 키로 다시 시도할 수 있다.

### 조회

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `GET /orders/me` | BUYER | — | `[{ "orderId", "totalPrice", "orderedAt", "itemCount" }]` (최신순, 페이지 없음) | — |
| `GET /orders/{orderId}` | BUYER | — | `{ "orderId", "totalPrice", "orderedAt", "itemList": [OrderItemView] }` | 404 `3000`(없음), 404 `3001`(남의 주문) |

`OrderItemView`:
```json
{ "orderItemId", "product": { "productId", "name", "thumbnailUrl", "seller": UserSummary },
  "quantity", "linePrice", "usedCoupon": UsedCoupon, "finalLinePrice", "status", "deliveredAt" }
```
- `usedCoupon`과 `deliveredAt`은 값이 없으면 필드 자체가 빠진다.
- `product`는 주문 시점 스냅샷이다.

## OrderItem

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `GET /order-items` | AUTH | 쿼리: `orderId` **또는** `sellerId` 중 정확히 하나, `statusList`(판매자 조회만, 여러 값), `page`, `size`(20/50/100) | 페이지(아래) | 404 `0001`(역할 불일치), 404 `3000`(남의 주문), 404 `3001`(`sellerId`가 본인이 아님). 조합·크기 오류는 현재 500 |
| `GET /order-items/{orderItemId}` | AUTH (구매자, 해당 판매자) | — | `{ "orderItemListRes": OrderItemView }` | 404 `3002`, 404 `3001` |
| `PATCH /order-items/{id}/ship` | SELLER | — | 200 | 400 `3003`(상태 전이 불가), 404 `3002`/`3001`, 409 `3004` |
| `PATCH /order-items/{id}/deliver` | SELLER | — | 200 | 위와 같음 |
| `PATCH /order-items/{id}/confirm` | BUYER | — | 200 | 위와 같음 |
| `PATCH /order-items/{id}/cancel` | AUTH (구매자, 해당 판매자) | — | 200. **이미 취소된 항목도 200** | 400 `3003`(주문완료가 아님), 400 `7510`, 404 `3002`/`3001`, 409 `3004`, 500 `2500`/`2006` |

`GET /order-items` 페이지의 `items`:
- 구매자 조회(`orderId`, 항목 ID 오름차순):
  `{ "orderItemId", "product": { "id", "name", "thumbnailUrl", "unitPrice", "seller": UserSummary }, "quantity", "linePrice", "usedCoupon"(없으면 null), "finalLinePrice", "status" }`
- 판매자 조회(`sellerId`, 항목 ID 내림차순):
  `{ "orderItemId", "product": { "id", "name", "thumbnailUrl", "unitPrice" }, "quantity", "linePrice", "usedCoupon", "finalLinePrice", "buyer": UserSummary, "status" }`

## Coupon

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `POST /coupons/events` | ADMIN | `{ "name", "type": "FIXED_AMOUNT"|"PERCENT", "discountValue"(≥1, 정률은 ≤100), "maxDiscountAmount"(≥1), "initialQuantity"(≥1), "startAt", "endAt"("yyyy-MM-dd HH:mm:ss"), "timezone"(기본 "Asia/Seoul"), "validDurationAmount"(≥1), "durationUnit": "MINUTES"|"HOURS"|"DAYS" }` | 이벤트 ID | 400 `9001`(기간, 날짜 형식, 시간대, 할인값) |
| `GET /coupons/events/{couponEventId}` | BUYER | — | `{ "couponEventId", "name", "type", "discountValue", "maxDiscountAmount", "initialQuantity", "remainingQuantity", "startAt", "endAt", "validSeconds", "open" }` | 404 `7500`, 500 `7504` |
| `POST /coupons/events/{couponEventId}/issue` | BUYER | — | `{ "couponIssuedId", "couponEventId", "issuedAt", "expiresAt" }` | 400 `7503`(열린 이벤트 아님, 캐시 없음), 400 `7506`(이미 발급), 400 `7507`(소진), 404 `1000`, 500 `7505` |

- 이벤트가 Redis에 올라가 있지 않으면(시작 10분 전보다 이르거나 종료 후) `remainingQuantity`는 `null`이다.
- 내 쿠폰 목록을 조회하는 API는 없다. `couponIssuedId`는 발급 응답에서 얻는다.

## Review

| 호출 | 권한 | 입력 | 출력 | 오류 |
|---|---|---|---|---|
| `POST /products/{productId}/reviews` | BUYER | `{ "halfStars"(0~10), "content"(≤255, 선택) }` | 리뷰 ID | 400(검증), 403 `6001`(구매확정 이력 없음), 403 `6002`(이미 작성), 404 `2000`, 404 `1000`, 500 `2006` |
| `GET /reviews` | PUBLIC | 쿼리: `searchBy`(`PRODUCT`/`WRITER`, 필수), `productId` 또는 `writerId`(`searchBy`에 맞게), `page`, `size`(기본 20, 제한 없음) | 페이지(`productReviews` 또는 `userReviews`, 최신 작성순) | 400 `9001`(`searchBy` 누락), 404 `2000`(삭제되었거나 없는 상품), 404 `1000`(탈퇴했거나 없는 작성자). ID 누락은 현재 500 |
| `PATCH /reviews/{reviewId}` | BUYER | `{ "halfStars", "content" }` (`content`를 빼거나 `null`로 보내면 내용이 비워짐) | 200 | 400 `2004`(삭제된 상품), 404 `6000`(없음), 404 `6003`(남의 리뷰), 500 `2006` |

- `productReviews` 항목: `{ "reviewId", "writer": { "userId", "nickname" }, "halfStars", "content", "lastUpdatedAt", "isUpdated" }`. 탈퇴한 작성자의 닉네임은 `"삭제된 사용자입니다"`로 나온다.
- `userReviews` 항목은 User 절의 `UserReview`와 같다.

## 관리 엔드포인트 (포트 8090, 내부 전용)

- `GET /actuator/health`: `{"status":"UP"}`. liveness와 readiness probe를 포함한다.
- `GET /actuator/prometheus`: Micrometer 지표다. HikariCP 풀 이름 태그는 `main`, 부하테스트에서는 `loadtest`다.
