# Feature And API

기준:
- `O`: 현재 코드 기준으로 엔드포인트가 연결되어 있고, 핵심 로직도 구현된 상태
- 빈칸: 컨트롤러/핸들러 누락, 서비스 미구현, 또는 현재 상태로는 정상 동작을 기대하기 어려운 상태
- 채팅 관련 항목은 현재 범위 제외 상태이며, 문서 보존용으로만 유지한다.

권한 값:
ALL -> 인증 없이도(=유저 아니어도) 가능.
USER -> 인증된 모든 유저
BUYER -> 구매자
SELLER -> 판매자
ADMIN -> 관리자

## User

| 기능명      | API                            | 완성여부 | 권한  |
| -------- | ------------------------------ | ---- | --- |
| 회원 정보 조회 | `GET /api/users/{userId}`      | O    | USER   |
| 내 회원 정보 조회 | `GET /api/users/me`          | O    | USER   |
| 회원 목록 조회 | `GET /api/users`              | O    | ADMIN  |
| 회원가입     | `POST /api/users`              | O    | ALL   |
| 회원정보 수정  | `PATCH /api/users/me`          | O    | USER   |
| 비밀번호 변경  | `PATCH /api/users/me/password` | O    | USER   |
| 회원탈퇴     | `POST /api/users/me/withdraw`  | O    | USER   |

## Auth

| 기능명  | API                     | 완성여부 | 권한  |
| ---- | ----------------------- | ---- | --- |
| CSRF 토큰 조회 | `GET /api/auth/csrf` | O | ALL |
| 로그인  | `POST /api/auth/login`  | O    | ALL |
| 로그아웃 | `POST /api/auth/logout` | O    | ALL |

## Product

| 기능명                | API                                      | 완성여부 | 권한  |
| ------------------ | ---------------------------------------- | ---- | --- |
| 상품 등록              | `POST /api/products`                     | O    | SELLER   |
| 상품 목록 / 검색 / 정렬 조회 | `GET /api/products`                      | O    | ALL   |
| 상품 상세 조회           | `GET /api/products/{productId}`          | O    | ALL   |
| 상품 정보 수정           | `PATCH /api/products/{productId}`        | O    | SELLER   |
| 상품 재고 수정           | `PATCH /api/products/inventory`          | O    | SELLER   |
| 상품 이미지 수정          | `PATCH /api/products/{productId}/images` | O    | SELLER   |
| 상품 삭제              | `DELETE /api/products/{productId}`       | O    | SELLER   |
| 상품 카테고리 생성          | `POST /api/products/category`            | O    | ADMIN    |
| 상품 카테고리 목록 조회       | `GET /api/products/category`             | O    | ALL      |
| 상품 카테고리 삭제          | `DELETE /api/products/category/{categoryId}` | O | ADMIN |

- `GET /api/products` 조회 방식
  - 검색 조건: 상품명/설명 키워드, 판매자 ID, 카테고리 ID
  - 정렬 기준: `ORDER_COUNT`, `RATING`, `VIEW_COUNT`, `PRICE`, `REG_DATE`
  - 정렬 방향: `ASC`, `DESC`

## Media

| 기능명           | API                              | 완성여부 | 권한 |
| ------------- | -------------------------------- | ---- | --- |
| 이미지 업로드 URL 생성 | `POST /api/media/upload-url`      | O    | SELLER |
| 이미지 업로드 완료 처리 | `POST /api/media/upload-complete` | O    | SELLER |
| 업로드 이미지 목록 조회 | `GET /api/media/uploaded-images` | O    | ADMIN  |

## Cart

| 기능명           | API                                   | 완성여부 | 권한  |
| ------------- | ------------------------------------- | ---- | --- |
| 장바구니 상품 추가    | `POST /api/cart-items`                | O    | BUYER   |
| 장바구니 목록 조회    | `GET /api/cart-items`                 | O    | BUYER   |
| 장바구니 상품 수량 수정 | `PATCH /api/cart-items/quantity`      | O    | BUYER   |
| 장바구니 상품 삭제    | `DELETE /api/cart-items/{cartItemId}` | O    | BUYER   |

## Order

| 기능명          | API                                            | 완성여부 | 권한  |
| ------------ | ---------------------------------------------- | ---- | --- |
| 주문 생성        | `POST /api/orders`                             | O    | BUYER   |
| 구매자 주문 목록 조회 | `GET /api/orders/me`                           | O    | BUYER   |
| 구매자 주문 상세 조회 | `GET /api/orders/{orderId}`                    | O    | BUYER   |
| 주문항목 목록 조회   | `GET /api/order-items`                         | O    | USER   |
| 주문항목 상세 조회   | `GET /api/order-items/{orderItemId}`           | O    | USER   |
| 주문항목 배송중 처리  | `PATCH /api/order-items/{orderItemId}/ship`    | O    | SELLER   |
| 주문항목 배송완료 처리 | `PATCH /api/order-items/{orderItemId}/deliver` | O    | SELLER   |
| 주문항목 구매확정    | `PATCH /api/order-items/{orderItemId}/confirm` | O    | BUYER   |
| 주문항목 취소      | `PATCH /api/order-items/{orderItemId}/cancel`  | O    | USER   |

- `GET /api/order-items` 조회 방식
  - 구매자 조회: `orderId={orderId}`
  - 판매자 조회: `sellerId={sellerId}`
  - 판매자 조회 시 주문상태 필터링 가능: `statusList={ORDERED|SHIPPED|DELIVERED|PURCHASE_CONFIRMED|CANCELED}`
  - `orderId`와 `sellerId`는 동시에 사용할 수 없다.
  - 구매자 조회에서는 `statusList`를 사용할 수 없다.
  - 페이징은 `page`, `size`로 지정한다.
  - 페이지 사이즈는 20(기본값)/50/100만 허용한다.

아래 Payment부분은, 범위에서 제외함.
```
## Payment (범위 제외. 비활성화)

| 기능명   | API                                      | 완성여부 | 권한 |
| ----- | ---------------------------------------- | ---- | --- |
| 결제 생성 | `POST /api/payments`                     |      |  |
| 결제 조회 | `GET /api/payments/{paymentId}`          |      |  |
| 결제 취소 | `PATCH /api/payments/{paymentId}/cancel` |      |  |
```


## Review

| 기능명      | API                                      | 완성여부 | 권한  |
| -------- | ---------------------------------------- | ---- | --- |
| 리뷰 작성    | `POST /api/products/{productId}/reviews` | O    | BUYER   |
| 리뷰 목록 조회 | `GET /api/reviews`                       | O    | ALL   |
| 리뷰 수정    | `PATCH /api/reviews/{reviewId}`          | O    | BUYER   |

- `GET /api/reviews` 조회 방식
  - 상품 리뷰 목록: `searchBy=PRODUCT&productId={productId}`
  - 유저 작성 리뷰 목록: `searchBy=WRITER&writerId={writerId}`

## Chat Room (비활성화 / 현재 범위 제외)

| 기능명          | API                                        | 완성여부 | 권한  |
| ------------ | ------------------------------------------ | ---- | --- |
| 채팅방 생성       | `POST /api/chat-rooms`                     |      | BUYER   |
| 채팅방 목록 조회    | `GET /api/chat-rooms`                      |      | USER   |
| 채팅방 상세 조회    | `GET /api/chat-rooms/{chatRoomId}`         |      | USER   |
| 채팅방 나가기      | `PATCH /api/chat-rooms/{chatRoomId}/leave` |      | USER   |
| 채팅 메시지 목록 조회 | `GET /api/chat-rooms/{chatRoomId}/chats`   |      | USER   |

## Chat Realtime (비활성화 / 현재 범위 제외)

| 기능명             | API                                                            | 완성여부 | 권한  |
| --------------- | -------------------------------------------------------------- | ---- | --- |
| 채팅 WebSocket 연결 | `WS CONNECT /ws/chat`                                          |      | USER   |
| 채팅 메시지 구독       | `WS SUB /topic/chat-rooms/{chatRoomId}`                        |      | USER   |
| 채팅 메시지 전송       | `WS PUB /app/chat-rooms/{chatRoomId}/messages`                 |      | USER   |
| 채팅 메시지 수정       | `WS PUB /app/chat-rooms/{chatRoomId}/messages/{chatId}/modify` |      | USER   |
| 채팅 메시지 삭제       | `WS PUB /app/chat-rooms/{chatRoomId}/messages/{chatId}/delete` |      | USER   |
