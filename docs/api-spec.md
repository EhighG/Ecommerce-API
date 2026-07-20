# API 명세

기준 경로는 `/api`이다. 아래 문서의 path에는 `/api` prefix를 생략한다.

## 공통 규칙

- 인증은 세션 쿠키 `JSESSIONID` 기반이다.
- `POST`, `PATCH`, `DELETE` 요청은 CSRF 토큰이 필요하다.
- CSRF 토큰은 `GET /auth/csrf`로 조회하고, 응답의 `headerName`을 헤더명으로 사용해 `token` 값을 전송한다.
- 인증 실패는 `401`, 권한 부족은 `403`으로 처리된다.
- 애플리케이션 오류와 validation 오류는 아래와 같이 code와 message를 포함한다.

```json
{
  "code": 9001,
  "message": "Invalid input"
}
```

### 권한 표기

| 표기            | 의미                |
| --------------- | ------------------- |
| `PUBLIC`        | 인증 없이 호출 가능 |
| `AUTHENTICATED` | 로그인한 사용자     |
| `BUYER`         | 구매자              |
| `SELLER`        | 판매자              |
| `ADMIN`         | 관리자              |

## 요약

| 도메인        | Base Path                           | 기능                                                | Method | Path                                    | 요구 권한       |
| ------------- | ----------------------------------- | --------------------------------------------------- | ------ | --------------------------------------- | --------------- |
| **Auth**      | **`/auth`**                         | [CSRF 토큰 조회](#csrf-토큰-조회)                   | GET    | `/auth/csrf`                            | `PUBLIC`        |
|               |                                     | [로그인](#로그인)                                   | POST   | `/auth/login`                           | `PUBLIC`        |
|               |                                     | [로그아웃](#로그아웃)                               | POST   | `/auth/logout`                          | `PUBLIC`        |
| **User**      | **`/users`**                        | [회원 가입](#회원-가입)                             | POST   | `/users`                                | `PUBLIC`        |
|               |                                     | [회원 프로필 조회](#회원-프로필-조회)               | GET    | `/users/{userId}`                       | `AUTHENTICATED` |
|               |                                     | [내 프로필 조회](#내-프로필-조회)                   | GET    | `/users/me`                             | `AUTHENTICATED` |
|               |                                     | [회원 목록 조회](#회원-목록-조회)                   | GET    | `/users`                                | `ADMIN`         |
|               |                                     | [내 정보 수정](#내-정보-수정)                       | PATCH  | `/users/me`                             | `AUTHENTICATED` |
|               |                                     | [비밀번호 변경](#비밀번호-변경)                     | PATCH  | `/users/me/password`                    | `AUTHENTICATED` |
|               |                                     | [회원 탈퇴](#회원-탈퇴)                             | POST   | `/users/me/withdraw`                    | `AUTHENTICATED` |
| **Product**   | **`/products`**                     | [상품 등록](#상품-등록)                             | POST   | `/products`                             | `SELLER`        |
|               |                                     | [상품 목록/검색/정렬 조회](#상품-목록검색정렬-조회) | GET    | `/products`                             | `PUBLIC`        |
|               |                                     | [상품 상세 조회](#상품-상세-조회)                   | GET    | `/products/{productId}`                 | `PUBLIC`        |
|               |                                     | [상품 정보 수정](#상품-정보-수정)                   | PATCH  | `/products/{productId}`                 | `SELLER`        |
|               |                                     | [상품 이미지 교체](#상품-이미지-교체)               | PATCH  | `/products/{productId}/images`          | `SELLER`        |
|               |                                     | [상품 삭제](#상품-삭제)                             | DELETE | `/products/{productId}`                 | `SELLER`        |
|               |                                     | [상품 카테고리 생성](#상품-카테고리-생성)           | POST   | `/products/category`                    | `ADMIN`         |
|               |                                     | [상품 카테고리 목록 조회](#상품-카테고리-목록-조회) | GET    | `/products/category`                    | `PUBLIC`        |
|               |                                     | [상품 카테고리 삭제](#상품-카테고리-삭제)           | DELETE | `/products/category/{categoryId}`       | `ADMIN`         |
| **Inventory** | **`/products`**                     | [상품 재고 수정](#상품-재고-수정)                   | PATCH  | `/products/inventory`                   | `SELLER`        |
| **Media**     | **`/media`**                        | [이미지 업로드 URL 생성](#이미지-업로드-url-생성)   | POST   | `/media/upload-url`                     | `SELLER`        |
|               |                                     | [이미지 업로드 완료 처리](#이미지-업로드-완료-처리) | POST   | `/media/upload-complete`                | `SELLER`        |
|               |                                     | [업로드 이미지 목록 조회](#업로드-이미지-목록-조회) | GET    | `/media/uploaded-images`                | `ADMIN`         |
| **Cart**      | **`/cart-items`**                   | [장바구니 상품 추가](#장바구니-상품-추가)           | POST   | `/cart-items`                           | `BUYER`         |
|               |                                     | [장바구니 목록 조회](#장바구니-목록-조회)           | GET    | `/cart-items`                           | `BUYER`         |
|               |                                     | [장바구니 수량 변경](#장바구니-수량-변경)           | PATCH  | `/cart-items/quantity`                  | `BUYER`         |
|               |                                     | [장바구니 항목 삭제](#장바구니-항목-삭제)           | DELETE | `/cart-items/{cartItemId}`              | `BUYER`         |
| **Order**     | **`/orders`**                       | [주문 생성](#주문-생성)                             | POST   | `/orders`                               | `BUYER`         |
|               |                                     | [내 주문 목록 조회](#내-주문-목록-조회)             | GET    | `/orders/me`                            | `BUYER`         |
|               |                                     | [내 주문 상세 조회](#내-주문-상세-조회)             | GET    | `/orders/{orderId}`                     | `BUYER`         |
| **OrderItem** | **`/order-items`**                  | [주문 항목 목록 조회](#주문-항목-목록-조회)         | GET    | `/order-items`                          | `AUTHENTICATED` |
|               |                                     | [주문 항목 상세 조회](#주문-항목-상세-조회)         | GET    | `/order-items/{orderItemId}`            | `AUTHENTICATED` |
|               |                                     | [주문 항목 배송중 처리](#주문-항목-배송중-처리)     | PATCH  | `/order-items/{orderItemId}/ship`       | `SELLER`        |
|               |                                     | [주문 항목 배송완료 처리](#주문-항목-배송완료-처리) | PATCH  | `/order-items/{orderItemId}/deliver`    | `SELLER`        |
|               |                                     | [주문 항목 구매확정](#주문-항목-구매확정)           | PATCH  | `/order-items/{orderItemId}/confirm`    | `BUYER`         |
|               |                                     | [주문 항목 취소](#주문-항목-취소)                   | PATCH  | `/order-items/{orderItemId}/cancel`     | `AUTHENTICATED` |
| **Coupon**    | **`/coupons`**                      | [쿠폰 이벤트 상세 조회](#쿠폰-이벤트-상세-조회)     | GET    | `/coupons/events/{couponEventId}`       | `BUYER`         |
|               |                                     | [쿠폰 발급](#쿠폰-발급)                             | POST   | `/coupons/events/{couponEventId}/issue` | `BUYER`         |
|               |                                     | [쿠폰 이벤트 생성](#쿠폰-이벤트-생성)               | POST   | `/coupons/events`                       | `ADMIN`         |
| **Review**    | **`/products/{productId}/reviews`** | [리뷰 작성](#리뷰-작성)                             | POST   | `/products/{productId}/reviews`         | `BUYER`         |
|               | **`/reviews`**                      | [리뷰 목록 조회](#리뷰-목록-조회)                   | GET    | `/reviews`                              | `PUBLIC`        |
|               |                                     | [리뷰 수정](#리뷰-수정)                             | PATCH  | `/reviews/{reviewId}`                   | `BUYER`         |

## Auth

### CSRF 토큰 조회

GET `/auth/csrf`

요구 권한: `PUBLIC`

#### 요청

요청 파라미터 없음.

#### 응답

성공 응답:

```json
{
  "headerName": "X-CSRF-TOKEN",
  "paramName": "_csrf",
  "token": "csrf-token-value"
}
```

실패 응답:

- `500`: 서버 내부 오류

```json
{
  "code": 9999,
  "message": "잠시 후 다시 시도해주세요."
}
```

### 로그인

POST `/auth/login`

요구 권한: `PUBLIC`

#### 요청

Body:

```json
{
  "email": "buyer@example.com",
  "password": "password"
}
```

요청 필드:

| 구분             | 이름       | 필수 | 설명            |
| ---------------- | ---------- | ---- | --------------- |
| **Request Body** | `email`    | O    | 로그인 이메일   |
|                  | `password` | O    | 로그인 비밀번호 |

#### 응답

성공 응답:

- `200 OK`
- body 없음
- `Set-Cookie: JSESSIONID=...`

실패 응답:

- `403`: CSRF 토큰 누락 또는 불일치
- `401`: 인증 실패
- `400`: 요청 body 파싱 실패 가능

인증 실패나 보안 필터 단계의 실패는 body가 없을 수 있다.

### 로그아웃

POST `/auth/logout`

요구 권한: `PUBLIC`

#### 요청

Header:

| 이름     | 필수 | 설명                    |
| -------- | ---- | ----------------------- |
| `Cookie` |      | 로그아웃할 `JSESSIONID` |

Body 없음.

#### 응답

성공 응답:

- `200 OK`
- body 없음
- 기존 session 무효화, `JSESSIONID` 삭제

실패 응답:

- `403`: CSRF 토큰 누락 또는 불일치

## User

### 회원 가입

POST `/users`

요구 권한: `PUBLIC`

#### 요청

Body:

```json
{
  "email": "buyer@example.com",
  "nickname": "buyer",
  "password": "password",
  "passwordCheck": "password",
  "role": "BUYER"
}
```

요청 필드:

| 구분             | 이름            | 필수 | 설명                                     |
| ---------------- | --------------- | ---- | ---------------------------------------- |
| **Request Body** | `email`         | O    | 이메일 형식                              |
|                  | `nickname`      | O    | 최대 20자                                |
|                  | `password`      | O    | 비밀번호                                 |
|                  | `passwordCheck` | O    | `password`와 동일해야 함                 |
|                  | `role`          | O    | `BUYER` 또는 `SELLER`. `ADMIN` 가입 불가 |

#### 응답

성공 응답:

```json
1
```

실패 응답:

- `400`: 비밀번호 확인 불일치, 잘못된 role, validation 실패
- `409`: 이메일 중복

```json
{
  "code": 1001,
  "message": "Email already in use"
}
```

### 회원 프로필 조회

GET `/users/{userId}`

요구 권한: `AUTHENTICATED`

#### 요청

| 구분              | 이름     | 필수 | 설명             |
| ----------------- | -------- | ---- | ---------------- |
| **Path Variable** | `userId` | O    | 조회할 사용자 ID |

#### 응답

성공 응답:

```json
{
  "userId": 1,
  "email": "buyer@example.com",
  "nickname": "buyer",
  "role": "BUYER",
  "joinDate": "2026-07-14",
  "reviewSection": {
    "totalCount": 1,
    "reviewList": []
  }
}
```

실패 응답:

- `401`: 인증 필요
- `404`: 사용자 없음

```json
{
  "code": 1000,
  "message": "User not found"
}
```

### 내 프로필 조회

GET `/users/me`

요구 권한: `AUTHENTICATED`

#### 요청

요청 파라미터 없음. 현재 로그인한 사용자 기준으로 조회한다.

#### 응답

성공 응답:

```json
{
  "userId": 1,
  "email": "buyer@example.com",
  "nickname": "buyer",
  "role": "BUYER",
  "joinDate": "2026-07-14"
}
```

실패 응답:

- `401`: 인증 필요
- `404`: 사용자 없음

```json
{
  "code": 1000,
  "message": "User not found"
}
```

### 회원 목록 조회

GET `/users`

요구 권한: `ADMIN`

#### 요청

요청 파라미터 없음.

#### 응답

성공 응답:

```json
[
  {
    "id": 1,
    "nickname": "buyer",
    "email": "buyer@example.com",
    "role": "BUYER",
    "deleted": false
  }
]
```

실패 응답:

- `401`: 인증 필요
- `403`: 관리자 권한 필요

### 내 정보 수정

PATCH `/users/me`

요구 권한: `AUTHENTICATED`

#### 요청

Body:

```json
{
  "nickname": "new-nickname"
}
```

요청 필드:

| 구분             | 이름       | 필수 | 설명                     |
| ---------------- | ---------- | ---- | ------------------------ |
| **Request Body** | `nickname` | O    | 변경할 닉네임. 공백 불가 |

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 입력 오류
- `401`: 인증 필요
- `404`: 사용자 없음

```json
{
  "code": 9001,
  "message": "Invalid input"
}
```

### 비밀번호 변경

PATCH `/users/me/password`

요구 권한: `AUTHENTICATED`

#### 요청

Body:

```json
{
  "oldPassword": "old-password",
  "newPassword": "new-password"
}
```

요청 필드:

| 구분             | 이름          | 필수 | 설명          |
| ---------------- | ------------- | ---- | ------------- |
| **Request Body** | `oldPassword` | O    | 기존 비밀번호 |
|                  | `newPassword` | O    | 새 비밀번호   |

#### 응답

성공 응답:

- `200 OK`
- body 없음
- 성공 후 로그아웃 처리

실패 응답:

- `400`: 입력 오류
- `401`: 기존 비밀번호 불일치 또는 인증 필요
- `404`: 사용자 없음

```json
{
  "code": 1003,
  "message": "Wrong password"
}
```

### 회원 탈퇴

POST `/users/me/withdraw`

요구 권한: `AUTHENTICATED`

#### 요청

Body:

```json
{
  "password": "password"
}
```

요청 필드:

| 구분             | 이름       | 필수 | 설명          |
| ---------------- | ---------- | ---- | ------------- |
| **Request Body** | `password` | O    | 현재 비밀번호 |

#### 응답

성공 응답:

- `200 OK`
- body 없음
- 성공 후 로그아웃 처리

실패 응답:

- `400`: 입력 오류
- `401`: 비밀번호 불일치 또는 인증 필요
- `403`: 진행 중 주문이 있어 탈퇴 불가
- `404`: 사용자 없음

```json
{
  "code": 1004,
  "message": "진행중인 주문이 있어 탈퇴가 불가능합니다."
}
```

## Product

### 상품 등록

POST `/products`

요구 권한: `SELLER`

#### 요청

Body:

```json
{
  "name": "상품명",
  "categoryId": 1,
  "description": "상품 설명",
  "unitPrice": 10000,
  "initialInventory": 100,
  "imageIdList": [
    {
      "imageId": 1,
      "order": 1
    }
  ]
}
```

요청 필드:

| 구분             | 이름                    | 필수 | 설명                                           |
| ---------------- | ----------------------- | ---- | ---------------------------------------------- |
| **Request Body** | `name`                  | O    | 상품명. 최대 50자                              |
|                  | `categoryId`            | O    | 상품 카테고리 ID                               |
|                  | `description`           | O    | 1~1000자                                       |
|                  | `unitPrice`             | O    | 0 이상                                         |
|                  | `initialInventory`      | O    | 0 이상                                         |
|                  | `imageIdList`           |      | 이미지 ID와 표시 순서. `null`이면 빈 배열 처리 |
|                  | `imageIdList[].imageId` |      | 업로드 완료된 이미지 ID                        |
|                  | `imageIdList[].order`   |      | 이미지 표시 순서. 1 이상                       |

#### 응답

성공 응답:

```json
1
```

실패 응답:

- `400`: 카테고리 없음, 이미지 소유자 불일치, 이미지 없음, 이미 사용 중인 이미지, 입력 오류
- `401`: 인증 필요
- `403`: 판매자 권한 필요

```json
{
  "code": 2001,
  "message": "Product category not found"
}
```

### 상품 목록/검색/정렬 조회

GET `/products`

요구 권한: `PUBLIC`

#### 요청

Query parameter:

| 구분            | 이름         | 필수 | 설명                                                                          |
| --------------- | ------------ | ---- | ----------------------------------------------------------------------------- |
| **Query Param** | `keyword`    |      | 상품명/설명 검색어                                                            |
|                 | `categoryId` |      | 카테고리 ID                                                                   |
|                 | `sellerId`   |      | 판매자 ID                                                                     |
|                 | `page`       |      | 기본값 0                                                                      |
|                 | `size`       |      | 20, 50, 100만 허용. 기본값 20                                                 |
|                 | `sortBy`     |      | `ORDER_COUNT`, `RATING`, `VIEW_COUNT`, `PRICE`, `REG_DATE`. 기본값 `REG_DATE` |
|                 | `direction`  |      | `ASC`, `DESC`. 기본값 `DESC`                                                  |

#### 응답

성공 응답:

```json
{
  "items": [
    {
      "id": 1,
      "name": "상품명",
      "categoryName": "상의",
      "unitPrice": 10000,
      "thumbnailImageUrl": "https://storage.googleapis.com/bucket/image.jpg",
      "seller": {
        "id": 2,
        "nickname": "seller"
      },
      "inventoryQuantity": 100,
      "avgRating": 4.5
    }
  ],
  "page": 0,
  "size": 20,
  "totalCount": 1,
  "totalPages": 1,
  "hasNext": false
}
```

실패 응답:

- `400`: page/size/sort 값 오류

```json
{
  "code": 9001,
  "message": "Invalid page size"
}
```

### 상품 상세 조회

GET `/products/{productId}`

요구 권한: `PUBLIC`

#### 요청

요청 파라미터:

| 구분              | 이름        | 필수 | 설명    |
| ----------------- | ----------- | ---- | ------- |
| **Path Variable** | `productId` | O    | 상품 ID |

#### 응답

성공 응답:

```json
{
  "id": 1,
  "name": "상품명",
  "category": {
    "id": 1,
    "name": "상의"
  },
  "unitPrice": 10000,
  "description": "상품 설명",
  "imageUrls": ["https://storage.googleapis.com/bucket/image.jpg"],
  "seller": {
    "id": 2,
    "nickname": "seller"
  },
  "inventoryQuantity": 100,
  "avgRating": 4.5
}
```

실패 응답:

- `404`: 상품 없음

```json
{
  "code": 2000,
  "message": "Product not found"
}
```

### 상품 정보 수정

PATCH `/products/{productId}`

요구 권한: `SELLER`

#### 요청

| 구분              | 이름          | 필수 | 설명                                         |
| ----------------- | ------------- | ---- | -------------------------------------------- |
| **Path Variable** | `productId`   | O    | 상품 ID                                      |
| **Request Body**  | `description` |      | 수정할 상품 설명. 1~1000자, 공백 문자열 불가 |
|                   | `unitPrice`   |      | 수정할 상품 가격. 0 이상                     |

Body:

```json
{
  "description": "수정된 상품 설명",
  "unitPrice": 12000
}
```

`description`, `unitPrice` 중 하나 이상 필요하다.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 삭제된 상품, 입력 오류
- `401`: 인증 필요
- `403`: 판매자 불일치
- `404`: 상품 없음

```json
{
  "code": 2002,
  "message": "권한이 없습니다."
}
```

### 상품 이미지 교체

PATCH `/products/{productId}/images`

요구 권한: `SELLER`

#### 요청

요청 파라미터:

| 구분              | 이름                    | 필수 | 설명                                                  |
| ----------------- | ----------------------- | ---- | ----------------------------------------------------- |
| **Path Variable** | `productId`             | O    | 상품 ID                                               |
| **Request Body**  | `imageIdList`           |      | 교체할 이미지 ID와 표시 순서. `null`이면 빈 배열 처리 |
|                   | `imageIdList[].imageId` |      | 업로드 완료된 이미지 ID                               |
|                   | `imageIdList[].order`   |      | 이미지 표시 순서. 1 이상                              |

Body:

```json
{
  "imageIdList": [
    {
      "imageId": 1,
      "order": 1
    }
  ]
}
```

`imageIdList`가 `null`이면 빈 배열로 처리되어 이미지 전체 제거가 가능하다.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 이미지 소유자 불일치, 이미지 없음, 이미 사용 중인 이미지, 입력 오류
- `401`: 인증 필요
- `403`: 판매자 불일치
- `404`: 상품 없음

```json
{
  "code": 8004,
  "message": "Image already in use"
}
```

### 상품 삭제

DELETE `/products/{productId}`

요구 권한: `SELLER`

#### 요청

요청 파라미터:

| 구분              | 이름        | 필수 | 설명    |
| ----------------- | ----------- | ---- | ------- |
| **Path Variable** | `productId` | O    | 상품 ID |

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `401`: 인증 필요
- `403`: 판매자 불일치
- `404`: 상품 없음

```json
{
  "code": 2000,
  "message": "Product not found"
}
```

### 상품 카테고리 생성

POST `/products/category`

요구 권한: `ADMIN`

#### 요청

Body:

```json
{
  "name": "상의"
}
```

요청 필드:

| 구분             | 이름   | 필수 | 설명                  |
| ---------------- | ------ | ---- | --------------------- |
| **Request Body** | `name` | O    | 카테고리명. 최대 15자 |

`name`은 최대 15자이다.

#### 응답

성공 응답:

```json
1
```

실패 응답:

- `400`: 입력 오류
- `401`: 인증 필요
- `403`: 관리자 권한 필요
- `409`: 카테고리명 중복

```json
{
  "code": 2005,
  "message": "Product category already exists"
}
```

### 상품 카테고리 목록 조회

GET `/products/category`

요구 권한: `PUBLIC`

#### 요청

요청 파라미터 없음.

#### 응답

성공 응답:

```json
[
  {
    "id": 1,
    "name": "상의"
  }
]
```

실패 응답:

- `500`: 서버 내부 오류

```json
{
  "code": 9999,
  "message": "잠시 후 다시 시도해주세요."
}
```

### 상품 카테고리 삭제

DELETE `/products/category/{categoryId}`

요구 권한: `ADMIN`

#### 요청

| 구분              | 이름         | 필수 | 설명        |
| ----------------- | ------------ | ---- | ----------- |
| **Path Variable** | `categoryId` | O    | 카테고리 ID |

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 카테고리 없음
- `401`: 인증 필요
- `403`: 관리자 권한 필요

```json
{
  "code": 2001,
  "message": "Product category not found"
}
```

### 상품 재고 수정

PATCH `/products/inventory`

요구 권한: `SELLER`

#### 요청

Body:

```json
{
  "productId": 1,
  "quantity": 100
}
```

요청 필드:

| 구분             | 이름        | 필수 | 설명                           |
| ---------------- | ----------- | ---- | ------------------------------ |
| **Request Body** | `productId` | O    | 재고를 수정할 상품 ID          |
|                  | `quantity`  | O    | 변경 후 절대 재고 수량. 0 이상 |

`quantity`는 증감량이 아니라 변경 후 절대 수량이다.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 삭제된 상품, 입력 오류
- `401`: 인증 필요
- `403`: 판매자 불일치
- `500`: 재고 정보 없음

```json
{
  "code": 2500,
  "message": "해당 상품의 재고정보가 없습니다. (관리자 문의 필요)"
}
```

## Media

### 이미지 업로드 URL 생성

POST `/media/upload-url`

요구 권한: `SELLER`

#### 요청

Body:

```json
{
  "originalFileName": "product.jpg",
  "contentType": "image/jpeg"
}
```

요청 필드:

| 구분             | 이름               | 필수 | 설명                                   |
| ---------------- | ------------------ | ---- | -------------------------------------- |
| **Request Body** | `originalFileName` | O    | 원본 파일명                            |
|                  | `contentType`      | O    | 이미지 MIME 타입. `image/` prefix 필요 |

`contentType`은 `image/` prefix를 가져야 한다.

#### 응답

성공 응답:

```json
{
  "objectKey": "product_images/uuid-product.jpg",
  "uploadUrl": "https://storage.googleapis.com/...",
  "expiresAt": "2026-07-14T10:00:00Z"
}
```

실패 응답:

- `400`: content type 오류, 입력 오류
- `401`: 인증 필요
- `403`: 판매자 권한 필요

```json
{
  "code": 8001,
  "message": "Invalid media content type"
}
```

### 이미지 업로드 완료 처리

POST `/media/upload-complete`

요구 권한: `SELLER`

#### 요청

Body:

```json
{
  "objectKey": "product_images/uuid-product.jpg"
}
```

요청 필드:

| 구분             | 이름        | 필수 | 설명                          |
| ---------------- | ----------- | ---- | ----------------------------- |
| **Request Body** | `objectKey` | O    | 업로드 완료 처리할 object key |

#### 응답

성공 응답:

```json
{
  "imageId": 1,
  "objectKey": "product_images/uuid-product.jpg",
  "imageUrl": "https://storage.googleapis.com/bucket/product_images/uuid-product.jpg"
}
```

실패 응답:

- `400`: content type 오류, 이미지 소유자 불일치, 이미지 없음, 입력 오류
- `401`: 인증 필요
- `403`: 판매자 권한 필요
- `404`: GCS object 없음

```json
{
  "code": 8000,
  "message": "Media object not found"
}
```

### 업로드 이미지 목록 조회

GET `/media/uploaded-images`

요구 권한: `ADMIN`

#### 요청

요청 파라미터 없음.

#### 응답

성공 응답:

```json
[
  {
    "id": 1,
    "objectKey": "product_images/uuid-product.jpg",
    "uploadUserId": 2,
    "contentType": "image/jpeg",
    "fileSize": 12345,
    "attached": false
  }
]
```

실패 응답:

- `401`: 인증 필요
- `403`: 관리자 권한 필요

## Cart

### 장바구니 상품 추가

POST `/cart-items`

요구 권한: `BUYER`

#### 요청

Body:

```json
{
  "productId": 1,
  "quantity": 2
}
```

요청 필드:

| 구분             | 이름        | 필수 | 설명                    |
| ---------------- | ----------- | ---- | ----------------------- |
| **Request Body** | `productId` | O    | 장바구니에 담을 상품 ID |
|                  | `quantity`  | O    | 추가할 수량. 1 이상     |

`quantity`는 1 이상이다. 같은 상품이 이미 장바구니에 있으면 새 row 생성 대신 수량을 더한다.

#### 응답

성공 응답:

```json
1
```

실패 응답:

- `400`: 수량 오류, 입력 오류
- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `404`: 사용자 또는 상품 없음

```json
{
  "code": 9002,
  "message": "Quantity must be greater than 0"
}
```

### 장바구니 목록 조회

GET `/cart-items`

요구 권한: `BUYER`

#### 요청

요청 파라미터 없음.

#### 응답

성공 응답:

```json
[
  {
    "id": 1,
    "product": {
      "id": 1,
      "name": "상품명",
      "categoryName": "상의",
      "unitPrice": 10000,
      "thumbnailImageUrl": "https://storage.googleapis.com/bucket/image.jpg",
      "seller": {
        "id": 2,
        "nickname": "seller"
      },
      "inventoryQuantity": 100,
      "avgRating": 4.5
    },
    "quantity": 2,
    "linePrice": 20000
  }
]
```

실패 응답:

- `401`: 인증 필요
- `403`: 구매자 권한 필요

### 장바구니 수량 변경

PATCH `/cart-items/quantity`

요구 권한: `BUYER`

#### 요청

Body:

```json
{
  "productId": 1,
  "quantity": 3
}
```

요청 필드:

| 구분             | 이름        | 필수 | 설명                      |
| ---------------- | ----------- | ---- | ------------------------- |
| **Request Body** | `productId` | O    | 수량을 변경할 상품 ID     |
|                  | `quantity`  | O    | 변경 후 절대 수량. 1 이상 |

`quantity`는 변경 후 절대 수량이며 1 이상이다.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 수량 오류, 입력 오류
- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `404`: 사용자 또는 장바구니 항목 없음

```json
{
  "code": 7000,
  "message": "CartItem not found"
}
```

### 장바구니 항목 삭제

DELETE `/cart-items/{cartItemId}`

요구 권한: `BUYER`

#### 요청

| 구분              | 이름         | 필수 | 설명             |
| ----------------- | ------------ | ---- | ---------------- |
| **Path Variable** | `cartItemId` | O    | 장바구니 항목 ID |

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `404`: 사용자 또는 장바구니 항목 없음

```json
{
  "code": 7000,
  "message": "CartItem not found"
}
```

## Order

### 주문 생성

POST `/orders`

요구 권한: `BUYER`

#### 요청

Header:

| 이름              | 필수 | 설명                                                                         |
| ----------------- | ---- | ---------------------------------------------------------------------------- |
| `Idempotency-Key` | O    | 주문 생성 중복 요청 식별용 key. 최대 128자, 영문자/숫자/`.`/`_`/`:`/`-` 허용 |

Body:

```json
{
  "items": [
    {
      "cartItemId": 1,
      "orderQuantity": 2,
      "couponIssuedId": 10
    }
  ]
}
```

요청 필드:

| 구분             | 이름                     | 필수 | 설명                    |
| ---------------- | ------------------------ | ---- | ----------------------- |
| **Request Body** | `items`                  | O    | 1개 이상                |
|                  | `items[].cartItemId`     | O    | 주문할 장바구니 항목 ID |
|                  | `items[].orderQuantity`  | O    | 주문 수량. 1 이상       |
|                  | `items[].couponIssuedId` |      | 사용할 발급 쿠폰 ID     |

#### 응답

성공 응답:

```json
1
```

같은 사용자, 같은 `Idempotency-Key`, 같은 payload의 재요청은 새 주문을 만들지 않고 기존 `orderId`를 반환한다.

실패 응답:

- `400`: 재고 부족, 장바구니 항목 없음, 쿠폰 만료/상태 오류/중복 사용, key 누락/형식 오류, 입력 오류
- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `409`: 같은 key에 다른 payload 사용, 동일 요청 처리 중
- `500`: 재고/상품 통계 정보 없음

```json
{
  "code": 9102,
  "message": "중복된 Idempotency-Key입니다."
}
```

### 내 주문 목록 조회

GET `/orders/me`

요구 권한: `BUYER`

#### 요청

요청 파라미터 없음.

#### 응답

성공 응답:

```json
[
  {
    "orderId": 1,
    "totalPrice": 20000,
    "orderedAt": "2026-07-14T10:00:00Z",
    "itemCount": 1
  }
]
```

실패 응답:

- `401`: 인증 필요
- `403`: 구매자 권한 필요

### 내 주문 상세 조회

GET `/orders/{orderId}`

요구 권한: `BUYER`

#### 요청

| 구분              | 이름      | 필수 | 설명    |
| ----------------- | --------- | ---- | ------- |
| **Path Variable** | `orderId` | O    | 주문 ID |

#### 응답

성공 응답:

```json
{
  "orderId": 1,
  "totalPrice": 20000,
  "orderedAt": "2026-07-14T10:00:00Z",
  "itemList": [
    {
      "orderItemId": 1,
      "product": {
        "productId": 1,
        "name": "상품명",
        "thumbnailUrl": "https://storage.googleapis.com/bucket/image.jpg",
        "seller": {
          "id": 2,
          "nickname": "seller"
        }
      },
      "quantity": 2,
      "linePrice": 20000,
      "finalLinePrice": 20000,
      "status": "ORDERED"
    }
  ]
}
```

실패 응답:

- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `404`: 주문 없음 또는 접근 불가

```json
{
  "code": 3000,
  "message": "주문정보를 찾을 수 없습니다"
}
```

## OrderItem

### 주문 항목 목록 조회

GET `/order-items`

요구 권한: `AUTHENTICATED`

#### 요청

Query parameter:

| 구분            | 이름         | 필수 | 설명                                                                                                          |
| --------------- | ------------ | ---- | ------------------------------------------------------------------------------------------------------------- |
| **Query Param** | `orderId`    |      | 구매자 기준 조회용 주문 ID. `sellerId`와 정확히 하나만 지정                                                   |
|                 | `sellerId`   |      | 판매자 기준 조회용 판매자 ID. `orderId`와 정확히 하나만 지정                                                  |
|                 | `statusList` |      | 판매자 기준 조회에서만 사용 가능. 허용값: `ORDERED`, `SHIPPED`, `DELIVERED`, `PURCHASE_CONFIRMED`, `CANCELED` |
|                 | `page`       |      | 기본값 0                                                                                                      |
|                 | `size`       |      | 20, 50, 100만 허용. 기본값 20                                                                                 |

#### 응답

구매자 기준 성공 응답:

```json
{
  "items": [
    {
      "orderItemId": 1,
      "product": {
        "id": 1,
        "name": "상품명",
        "thumbnailUrl": "https://storage.googleapis.com/bucket/image.jpg",
        "unitPrice": 10000,
        "seller": {
          "id": 2,
          "nickname": "seller"
        }
      },
      "quantity": 2,
      "linePrice": 20000,
      "usedCoupon": null,
      "finalLinePrice": 20000,
      "status": "ORDERED"
    }
  ],
  "page": 0,
  "size": 20,
  "totalCount": 1,
  "totalPages": 1,
  "hasNext": false
}
```

판매자 기준 성공 응답은 item에 `buyer`가 포함된다.

실패 응답:

- `400`: `orderId`/`sellerId` 조합 오류, 구매자 기준 조회의 `statusList` 사용 오류, page size 오류
- `401`: 인증 필요
- `404`: 권한 없음, 주문 없음 또는 접근 불가

```json
{
  "code": 9001,
  "message": "Invalid input"
}
```

### 주문 항목 상세 조회

GET `/order-items/{orderItemId}`

요구 권한: `AUTHENTICATED`

#### 요청

| 구분              | 이름          | 필수 | 설명         |
| ----------------- | ------------- | ---- | ------------ |
| **Path Variable** | `orderItemId` | O    | 주문 항목 ID |

#### 응답

성공 응답:

```json
{
  "orderItemListRes": {
    "orderItemId": 1,
    "product": {
      "productId": 1,
      "name": "상품명",
      "thumbnailUrl": "https://storage.googleapis.com/bucket/image.jpg",
      "seller": {
        "id": 2,
        "nickname": "seller"
      }
    },
    "quantity": 2,
    "linePrice": 20000,
    "finalLinePrice": 20000,
    "status": "ORDERED"
  }
}
```

실패 응답:

- `401`: 인증 필요
- `404`: 주문 항목 없음 또는 접근 불가

```json
{
  "code": 3002,
  "message": "OrderItem not found"
}
```

### 주문 항목 배송중 처리

PATCH `/order-items/{orderItemId}/ship`

요구 권한: `SELLER`

#### 요청

| 구분              | 이름          | 필수 | 설명                       |
| ----------------- | ------------- | ---- | -------------------------- |
| **Path Variable** | `orderItemId` | O    | 배송중 처리할 주문 항목 ID |

Body 없음.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 상태 전이 불가
- `401`: 인증 필요
- `403`: 판매자 권한 필요
- `404`: 주문 항목 없음 또는 접근 불가
- `409`: 동시 상태 변경 충돌

```json
{
  "code": 3004,
  "message": "주문 상태가 변경되었습니다. 다시 시도해주세요."
}
```

### 주문 항목 배송완료 처리

PATCH `/order-items/{orderItemId}/deliver`

요구 권한: `SELLER`

#### 요청

| 구분              | 이름          | 필수 | 설명                         |
| ----------------- | ------------- | ---- | ---------------------------- |
| **Path Variable** | `orderItemId` | O    | 배송완료 처리할 주문 항목 ID |

Body 없음.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 상태 전이 불가
- `401`: 인증 필요
- `403`: 판매자 권한 필요
- `404`: 주문 항목 없음 또는 접근 불가
- `409`: 동시 상태 변경 충돌

```json
{
  "code": 3003,
  "message": "해당 처리가 불가능한 주문입니다."
}
```

### 주문 항목 구매확정

PATCH `/order-items/{orderItemId}/confirm`

요구 권한: `BUYER`

#### 요청

| 구분              | 이름          | 필수 | 설명                         |
| ----------------- | ------------- | ---- | ---------------------------- |
| **Path Variable** | `orderItemId` | O    | 구매확정 처리할 주문 항목 ID |

Body 없음.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 상태 전이 불가
- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `404`: 주문 항목 없음 또는 접근 불가
- `409`: 동시 상태 변경 충돌

```json
{
  "code": 3003,
  "message": "해당 처리가 불가능한 주문입니다."
}
```

### 주문 항목 취소

PATCH `/order-items/{orderItemId}/cancel`

요구 권한: `AUTHENTICATED`

#### 요청

| 구분              | 이름          | 필수 | 설명                |
| ----------------- | ------------- | ---- | ------------------- |
| **Path Variable** | `orderItemId` | O    | 취소할 주문 항목 ID |

Body 없음.

이미 `CANCELED` 상태인 주문 항목에 대한 재요청은 성공 처리하고, 재고/쿠폰/상품 통계 복구 부수효과는 반복하지 않는다.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 상태 전이 불가, 쿠폰 없음
- `401`: 인증 필요
- `404`: 주문 항목 없음 또는 접근 불가
- `409`: 동시 상태 변경 충돌
- `500`: 상품 통계 또는 재고 정보 없음

```json
{
  "code": 3004,
  "message": "주문 상태가 변경되었습니다. 다시 시도해주세요."
}
```

## Coupon

### 쿠폰 이벤트 상세 조회

GET `/coupons/events/{couponEventId}`

요구 권한: `BUYER`

#### 요청

| 구분              | 이름            | 필수 | 설명           |
| ----------------- | --------------- | ---- | -------------- |
| **Path Variable** | `couponEventId` | O    | 쿠폰 이벤트 ID |

#### 응답

성공 응답:

```json
{
  "couponEventId": 1,
  "name": "10% 할인 쿠폰",
  "type": "PERCENT",
  "discountValue": 10,
  "maxDiscountAmount": 5000,
  "initialQuantity": 100,
  "remainingQuantity": 50,
  "startAt": "2026-07-14T00:00:00Z",
  "endAt": "2026-07-15T00:00:00Z",
  "validSeconds": 86400,
  "open": true
}
```

실패 응답:

- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `404`: 쿠폰 이벤트 없음
- `500`: 이벤트 정보 조회 실패

```json
{
  "code": 7500,
  "message": "해당 이벤트가 존재하지 않습니다."
}
```

### 쿠폰 발급

POST `/coupons/events/{couponEventId}/issue`

요구 권한: `BUYER`

#### 요청

| 구분              | 이름            | 필수 | 설명           |
| ----------------- | --------------- | ---- | -------------- |
| **Path Variable** | `couponEventId` | O    | 쿠폰 이벤트 ID |

Body 없음.

#### 응답

성공 응답:

```json
{
  "couponIssuedId": 1,
  "couponEventId": 1,
  "issuedAt": "2026-07-14T10:00:00Z",
  "expiresAt": "2026-07-15T10:00:00Z"
}
```

실패 응답:

- `400`: 이벤트 종료, 이미 발급됨, 쿠폰 소진
- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `404`: 사용자 없음
- `500`: 쿠폰 발급 실패

```json
{
  "code": 7506,
  "message": "해당 쿠폰은 1회만 발급 가능합니다."
}
```

### 쿠폰 이벤트 생성

POST `/coupons/events`

요구 권한: `ADMIN`

#### 요청

Body:

```json
{
  "name": "10% 할인 쿠폰",
  "type": "PERCENT",
  "discountValue": 10,
  "maxDiscountAmount": 5000,
  "initialQuantity": 100,
  "startAt": "2026-07-14 00:00:00",
  "endAt": "2026-07-15 00:00:00",
  "timezone": "Asia/Seoul",
  "validDurationAmount": 1,
  "durationUnit": "DAYS"
}
```

요청 필드:

| 구분             | 이름                  | 필수 | 설명                                    |
| ---------------- | --------------------- | ---- | --------------------------------------- |
| **Request Body** | `name`                | O    | 쿠폰 이벤트명                           |
|                  | `type`                | O    | `FIXED_AMOUNT` 또는 `PERCENT`           |
|                  | `discountValue`       | O    | 할인값. 양수, `PERCENT`는 1~100         |
|                  | `maxDiscountAmount`   | O    | 최대 할인 금액. 양수                    |
|                  | `initialQuantity`     | O    | 최초 발급 수량. 양수                    |
|                  | `startAt`             | O    | 이벤트 시작 일시. `yyyy-MM-dd HH:mm:ss` |
|                  | `endAt`               | O    | 이벤트 종료 일시. `yyyy-MM-dd HH:mm:ss` |
|                  | `timezone`            |      | 시간대. 기본값 `Asia/Seoul`             |
|                  | `validDurationAmount` | O    | 쿠폰 유효기간 수량. 양수                |
|                  | `durationUnit`        | O    | `MINUTES`, `HOURS`, `DAYS`              |

`type`은 `FIXED_AMOUNT` 또는 `PERCENT`이다. `PERCENT` 쿠폰의 `discountValue`는 1~100 범위여야 한다.

#### 응답

성공 응답:

```json
1
```

실패 응답:

- `400`: 이벤트 기간, 날짜 형식, 할인값, timezone, validation 오류
- `401`: 인증 필요
- `403`: 관리자 권한 필요

```json
{
  "code": 9001,
  "message": "Invalid coupon event period."
}
```

## Review

### 리뷰 작성

POST `/products/{productId}/reviews`

요구 권한: `BUYER`

#### 요청

요청 파라미터:

| 구분              | 이름        | 필수 | 설명                                      |
| ----------------- | ----------- | ---- | ----------------------------------------- |
| **Path Variable** | `productId` | O    | 리뷰를 작성할 상품 ID                     |
| **Request Body**  | `halfStars` | O    | 0~10 정수. 별점 0.0~5.0을 0.5 단위로 표현 |
|                   | `content`   |      | 리뷰 내용. 최대 255자                     |

Body:

```json
{
  "halfStars": 9,
  "content": "좋습니다."
}
```

`halfStars`는 0~10 정수이며, 별점 0.0~5.0을 0.5 단위로 표현한다.

#### 응답

성공 응답:

```json
1
```

실패 응답:

- `400`: 삭제된 상품, 별점/내용 validation 오류
- `401`: 인증 필요
- `403`: 구매확정 주문 없음, 이미 리뷰 작성
- `404`: 사용자 또는 상품 없음
- `500`: 상품 통계 없음

```json
{
  "code": 6002,
  "message": "상품당 1개의 리뷰만 작성 가능합니다."
}
```

### 리뷰 목록 조회

GET `/reviews`

요구 권한: `PUBLIC`

#### 요청

Query parameter:

| 구분            | 이름        | 필수 | 설명                                                              |
| --------------- | ----------- | ---- | ----------------------------------------------------------------- |
| **Query Param** | `searchBy`  | O    | `PRODUCT` 또는 `WRITER`                                           |
|                 | `productId` |      | `searchBy=PRODUCT`일 때 필수. `searchBy=WRITER`이면 사용하지 않음 |
|                 | `writerId`  |      | `searchBy=WRITER`일 때 필수. `searchBy=PRODUCT`이면 사용하지 않음 |
|                 | `page`      |      | 기본값 0                                                          |
|                 | `size`      |      | 기본값 20                                                         |

#### 응답

상품 기준 성공 응답:

```json
{
  "productReviews": [
    {
      "reviewId": 1,
      "writer": {
        "userId": 1,
        "nickname": "buyer"
      },
      "halfStars": 9,
      "content": "좋습니다.",
      "lastUpdatedAt": "2026-07-14T10:00:00Z",
      "isUpdated": false
    }
  ],
  "page": 0,
  "size": 20,
  "totalCount": 1,
  "totalPages": 1,
  "hasNext": false
}
```

작성자 기준 조회 시에는 `userReviews`가 채워지고 `productReviews`는 생략된다.

실패 응답:

- `400`: `searchBy`에 필요한 ID 누락, validation 오류
- `404`: 사용자 또는 상품 없음

```json
{
  "code": 9001,
  "message": "Exactly one of Product Id or Writer Id is required"
}
```

### 리뷰 수정

PATCH `/reviews/{reviewId}`

요구 권한: `BUYER`

#### 요청

요청 파라미터:

| 구분              | 이름        | 필수 | 설명                                                       |
| ----------------- | ----------- | ---- | ---------------------------------------------------------- |
| **Path Variable** | `reviewId`  | O    | 수정할 리뷰 ID                                             |
| **Request Body**  | `halfStars` | O    | 0~10 정수. 별점 0.0~5.0을 0.5 단위로 표현                  |
|                   | `content`   |      | 리뷰 내용. 최대 255자. 생략하거나 `null`이면 `null`로 교체 |

Body:

```json
{
  "halfStars": 8,
  "content": "수정된 리뷰 내용"
}
```

`content`를 생략하거나 `null`로 보내면 내용이 `null`로 교체된다.

#### 응답

성공 응답:

- `200 OK`
- body 없음

실패 응답:

- `400`: 삭제된 상품, 별점/내용 validation 오류
- `401`: 인증 필요
- `403`: 구매자 권한 필요
- `404`: 리뷰 없음 또는 작성자 불일치
- `500`: 상품 통계 없음

```json
{
  "code": 6003,
  "message": "Review not found"
}
```
