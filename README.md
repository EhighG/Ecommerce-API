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

![ER Diagram](docs/assets/ERD.png)
