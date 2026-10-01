# cartitem — 장바구니

장바구니 규칙은 `docs/business-rules.md`의 "장바구니"에 있다.

## 담당하지 않는 것
- 주문 후 장바구니 정리. `OrderPlacementService`가 이 패키지의 저장소와 엔티티 메서드로 직접 한다.
- 상품 삭제 시 장바구니 정리. `ProductDeletionService`가 bulk delete로 한다.
- 재고 검사. 담을 때는 하지 않고 주문할 때 한다.

## 항상 지켜야 할 것
- 수량이 0 이하가 되면 생성자, `changeQuantity`, `adjustQuantity`가 `ORDER_QUANTITY_MUST_PLUS`(`9002`)를 던진다.
- 담기(`addItem`)
  - 이미 담긴 상품이면 `adjustQuantity(+수량)`하고 기존 ID를 돌려준다.
  - 처음 담는 상품이면 `saveAndFlush`한다. 유니크 위반이면 `CART_ITEM_CONFLICT`(409, `7001`)다.
  - 이미 담긴 상품을 동시에 담을 때의 수량 유실은 `docs/tracking/findings/product.md`에 있다.
- 수량 변경은 **상품 ID로** 대상을 찾는다(`findByUserIdAndProductId`). 삭제는 **장바구니 항목 ID + 사용자 ID**로 찾는다. 남의 항목은 `CART_ITEM_NOT_FOUND`(404)다.
- 목록은 `findAllByUserIdWithProduct` DTO projection 한 번으로 가져온다.

## 테스트 기준
이 패키지를 바꾸면 단위 테스트(`CartItemTest`, `CartItemServiceTest`)로 아래를 확인한다.
- 새 상품 담기, 기존 상품 합산, 사용자·상품 없음, 유니크 충돌 → `7001`
- 썸네일 있음·없음 목록
- 수량 변경·삭제의 성공과 없음
- 경계 사례: 수량 0이나 음수, 삭제된 상품 담기 → 404 `2000`
