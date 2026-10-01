# Ecommerce-API

이커머스 서비스의 주문, 재고, 쿠폰 등 정합성과 신뢰성이 중요한 기능을 구현하고, <br>
실제 구매 플로우를 가정한 부하 테스트를 통해 트래픽 증가 시 발생하는 성능·정합성 문제를 관측, 개선

## 핵심 도메인과 기능

- 주문: 장바구니 항목으로 주문 생성(멱등 키로 중복 주문 방지), 주문항목별 배송·구매확정·취소
- 재고: 주문 시 조건부 차감으로 음수 재고와 초과 판매 방지, 취소 시 복구
- 쿠폰: Redis 선착순 발급(이벤트당 1인 1장), 주문 시 사용, 취소 시 복구
- 상품: 등록·수정·삭제, 전문 검색(FULLTEXT, ngram)과 정렬, 조회수 집계
- 그 밖에: 회원·세션 인증, 장바구니, 리뷰·평점, 상품 이미지 업로드(GCS 서명 URL)

API 명세: [Swagger UI](https://ehighg.github.io/Ecommerce-API/) · [openapi.yaml](docs/api/openapi.yaml)

## 주요 성과

**1. [주문 생성 DB 호출 수 감축을 통한 병목 완화](docs/cases/01-order-query-batching.md)** <br>
API 응답 시간 p95 4.88s -> 2.02s로 약 58.6% 개선

**2. [주문 생성, 취소 멱등성 보장](docs/cases/02-order-idempotency.md)** <br>
해당 API에서의 중복 요청 문제 (부수효과 중복 발생, 사용자 혼란 등) 제거

**3. [API VM Scale-Out을 통한 병목 해결](docs/cases/03-horizontal-scale-out.md)** <br>
API VM 병목 제거, 주문 시나리오 안정 처리량 55 iter/s -> 95 iter/s로 개선

## 아키텍처 다이어그램

![Architecture Diagram](docs/assets/Architecture_Diagram.png)

## ERD

관계선은 DB의 외래 키만 그렸고, 속성은 유니크·전문 검색 제약만 적었다. 컬럼 전체는 [`api/db/schema.sql`](api/db/schema.sql)에 있다.

```mermaid
erDiagram
    USERS ||--o{ PRODUCT : "seller_id"
    USERS ||--o{ CART_ITEM : "user_id"
    USERS ||--o{ ORDERS : "buyer_id"
    USERS ||--o{ COUPON_ISSUED : "user_id"
    USERS ||--o{ REVIEW : "writer_id"
    PRODUCT_CATEGORY ||--o{ PRODUCT : "product_category_id"
    PRODUCT ||--|| INVENTORY : "product_id"
    PRODUCT ||--|| PRODUCT_STAT : "product_id"
    PRODUCT ||--o{ CART_ITEM : "product_id"
    PRODUCT ||--o{ REVIEW : "product_id"
    PRODUCT |o--o| PRODUCT_IMAGE : "thumbnail_image_id"
    UPLOADED_IMAGE ||--o| PRODUCT_IMAGE : "uploaded_image_id"
    ORDERS ||--|{ ORDER_ITEM : "order_id"
    ORDER_ITEM ||--o| ORDER_ITEM_COUPON : "order_item_id"
    COUPON_EVENT ||--o{ COUPON_ISSUED : "coupon_event_id"

    USERS {
        varchar email UK
    }
    PRODUCT_CATEGORY {
        varchar name UK
    }
    PRODUCT {
        bigint thumbnail_image_id UK
        varchar name "FULLTEXT(name, description) WITH PARSER ngram"
    }
    INVENTORY {
        bigint product_id UK
    }
    PRODUCT_STAT {
        bigint product_id PK "상품 ID를 PK로 공유"
    }
    PRODUCT_IMAGE {
        bigint uploaded_image_id UK
        bigint product_id "UK(product_id, display_order)"
    }
    CART_ITEM {
        bigint user_id "UK(user_id, product_id)"
    }
    COUPON_ISSUED {
        bigint coupon_event_id "UK(coupon_event_id, user_id)"
    }
    ORDER_ITEM {
        bigint product_id "주문 시점 상품 스냅샷"
    }
    ORDER_ITEM_COUPON {
        bigint order_item_id UK
    }
    REVIEW {
        bigint writer_id "UK(writer_id, product_id)"
    }
    IDEMPOTENCY_RECORD {
        bigint user_id "UK(user_id, scope, idempotency_key)"
    }
```

외래 키가 없는 참조: `order_item`의 상품·판매자 ID와 `order_item_coupon`의 발급 쿠폰 ID(주문 시점 스냅샷), `product_image.product_id`, `uploaded_image.upload_user_id`, `idempotency_record.user_id`.
