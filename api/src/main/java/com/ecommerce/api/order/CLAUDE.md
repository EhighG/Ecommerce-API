# order — 주문 생성, 조회, 주문항목 상태 전이·취소

## 담당 범위
- 담당하는 것:
  - `Order`(헤더, `orders` 테이블), `OrderItem`(주문항목, 상태·버전)
  - 스냅샷 VO: `ProductSnapshot`, `UserSnapshot`, `OrderLine`
  - 주문 생성 흐름(`IdempotentOrderPlacementService` → `OrderPlacementService`)
  - 주문·주문항목 조회
  - 배송 시작·배송 완료·구매확정·취소
- 담당하지 않는 것:
  - 멱등 기록 저장과 판정(idempotency 패키지)
  - 할인 계산과 쿠폰 상태 변경(`CouponEvent`, `CouponIssued`)
  - 재고 SQL(`InventoryJdbcRepository`)
  - 통계 SQL(`ProductStatJdbcRepository`, `ProductStatRepository`)
  - 장바구니 엔티티 규칙
  
  이들은 호출만 하고 여기서 로직을 복제하지 않는다.

## 항상 지켜야 할 것
- 컨트롤러의 주문 생성은 `IdempotentOrderPlacementService.placeOrder`만 호출한다. `OrderPlacementService.placeOrder`는 멱등 래퍼가 연 트랜잭션 안에서만 실행된다.
- `OrderPlacementService.placeOrder`의 순서:
  1. 구매자 조회
  2. 장바구니 항목 일괄 조회(중복 ID 거절, 개수 불일치 시 `CART_ITEM_NOT_FOUND`, 삭제 상품 거절)
  3. 쿠폰 `FOR UPDATE` 조회
  4. 항목별 할인 계산과 `coupon.use(now)`
  5. `inventoryService.validateAndDeduct`
  6. `Order` 저장(cascade로 `OrderItem`)
  7. 통계 배치 증가
  8. 쿠폰 스냅샷 생성
  9. 장바구니 정리(항목 ID 오름차순)
  
  재고 차감을 통계 증가보다 뒤로 옮기면 락 순서가 뒤집혀 취소와 데드락이 난다.
- `OrderItemService.cancel`의 순서는 쿠폰 복구 → 재고 복구(`FOR UPDATE`) → 통계 −1이고, 락도 주문 생성과 같게 쿠폰 → 재고 → 통계 순서로 잡아야 한다. 현재는 쿠폰을 락 없이 읽고 커밋할 때 갱신해서 실제 락 순서가 재고 → 통계 → 쿠폰이다(`docs/tracking/findings/order.md`).
- `OrderItem.cancel()`은 이미 `CANCELED`면 `false`를 돌려준다. 이때 서비스는 복구 작업 없이 바로 성공으로 끝나야 한다.
- 상태는 `startDelivery()` / `delivered()` / `confirm()` / `cancel()` 메서드로만 바꾼다. `@Version`을 우회하는 JPQL/JDBC UPDATE로 상태를 바꾸지 않는다.
- 금액
  - `OrderItem.linePrice = unitPrice × quantity`(할인 전)
  - `Order.totalPrice = Σ OrderLine.finalLinePrice()`(할인 후)
  - 응답의 `finalLinePrice`는 `linePrice − 스냅샷 할인액`으로 계산한다.
  - 새 응답을 만들 때도 이 공식을 쓴다.
  - `Order.totalPrice`는 주문 시점 값이라 취소해도 바꾸지 않는다. 주문 상세의 현재 금액(`currentTotalPrice`)은 취소되지 않은 항목의 `finalLinePrice` 합으로 조회할 때 계산한다(저장 컬럼 없음). 현재는 이 필드가 없다.
- 판매자 판정(`isSeller`)과 판매자 기준 조회는 `OrderItem.product.seller.id`(주문 시점 스냅샷)로 한다. 현재 `Product.seller`를 조인하지 않는다.
- 권한이 없으면 `ORDER_ACCESS_DENIED`(404)를 쓴다. 역할이 맞지 않는 주문항목 목록 조회는 `NO_PERMISSIONS`(404)다.
- `createUsedCouponSnapshot`은 `orderLines`와 `saved.getItemList()`의 **인덱스가 같은 항목끼리** 짝짓는다. `Order` 생성자가 `orderLines` 순서대로 항목을 추가하기 때문이다. 이 순서를 바꾸면 쿠폰이 다른 항목에 붙는다.

## 알아둘 구현 방식
- 멱등 래퍼는 `TransactionTemplate(PROPAGATION_REQUIRES_NEW)`로 트랜잭션을 직접 나눈다.
  - 선점 중 `DataIntegrityViolationException`이 나면(동시 동일 요청) 한 번 다시 선점한다.
  - 본 처리가 `RuntimeException`으로 실패하면 `deleteProcessing`을 별도 트랜잭션에서 실행한다. 정리 실패는 `suppressed`로 붙인다.
- 요청 지문(`OrderRequestFingerprintGenerator`)은 항목을 `cartItemId`로 정렬한 뒤 `cartItemId`, `orderQuantity`, `couponIssuedId`를 문자열로 이어 SHA-256으로 만든다. 요청 필드를 추가하면 지문에도 넣을지 결정해야 한다. 넣지 않으면 다른 요청이 같은 요청으로 재응답된다.
- 주문 상세는 `findDetailById`(fetch join)를 쓴다. 쿠폰 스냅샷은 `OrderItemCouponService.findByOrderItemId(List)`로 한 번에 조회한다.
- 상품 상세에서 바로 주문하는 기능은 아직 없다. 추가할 때 지켜야 할 것:
  - 장바구니를 거치지 않는 입력(상품 ID, 수량)을 받는다.
  - 멱등 래퍼, 락 순서, 쿠폰 처리, 지문 규칙은 기존 흐름과 같게 한다.
- 자동 구매확정도 아직 없다. 대상은 `DELIVERED`이면서 `now >= (deliveredAt의 Asia/Seoul 날짜 + 4일) 00:00 Asia/Seoul`인 항목이다(예: 9/1 15:00 배송완료 → 9/5 00:00). `deliveredAt`은 UTC `Instant`로 저장되니 서울 날짜로 변환한 뒤 계산한다. 다중 인스턴스에서 동시에 돌아도 한 항목이 한 번만 확정되어야 하고, 구매자의 수동 확정과 겹쳐도 `@Version`이 이중 처리를 막는다.

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 통합 테스트(`OrderServiceIntegrationTestSupport`)로 확인할 것:
  - 첫 요청 성공과 멱등 기록 `SUCCEEDED`
  - 같은 키·같은 내용의 재응답(부수효과 없음)
  - 같은 키·다른 내용은 `9102`
  - 처리 중이면 `9103`
  - 실패 시 `PROCESSING` 정리
  - 취소 시 재고·통계·쿠폰 복구
  - 재취소는 부수효과 없음
  - 취소 불가 상태는 `3003`
  - 제3자는 `3001`
- 경계 사례:
  - 주문 수량 > 장바구니 수량이면 장바구니 항목이 삭제된다.
  - 주문 수량 < 장바구니 수량이면 장바구니 수량에서 뺀다.
  - 같은 상품이 두 항목이면 재고는 합계로 차감하고, 통계는 항목 수만큼 증가한다(현재 API로는 생기지 않는다).
  - 재고 부족이면 전체 롤백된다.
  - 쿠폰을 쓴 뒤 만료 시각이 지나서 취소하면 쿠폰이 `EXPIRED`가 된다.
  - 배송 시작·배송 완료·구매확정 전이, 동시 상태 변경 409.
