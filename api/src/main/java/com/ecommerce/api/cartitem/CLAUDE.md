# cartitem — 장바구니

## 담당 범위
- 담당하는 것: `CartItem`(유니크 `user_id + product_id`, 수량 ≥ 1), 담기, 목록, 수량 변경, 삭제.
- 담당하지 않는 것:
  - 주문 후 장바구니 정리. `OrderPlacementService`가 이 패키지의 저장소와 엔티티 메서드로 직접 한다.
  - 상품 삭제 시 장바구니 정리. `ProductDeletionService`가 bulk delete로 한다.
  - 재고 검사. 담을 때는 하지 않고 주문할 때 한다.

## 항상 지켜야 할 것
- 수량은 항상 1 이상이다. 생성자, `changeQuantity`, `adjustQuantity`가 0 이하면 `ORDER_QUANTITY_MUST_PLUS`(`9002`)를 던진다.
- 담기(`addItem`)
  - 구매자와 상품(삭제 제외)을 확인한다.
  - 이미 담긴 상품이면 `adjustQuantity(+수량)`하고 기존 ID를 돌려준다.
  - 처음 담는 상품이면 `saveAndFlush`한다. 유니크 위반이면 `CART_ITEM_CONFLICT`(409, `7001`)다.
- 수량 변경은 **상품 ID로** 대상을 찾는다(`findByUserIdAndProductId`). 삭제는 **장바구니 항목 ID + 사용자 ID**로 찾는다. 남의 항목은 `CART_ITEM_NOT_FOUND`(404)다.
- 목록은 `findAllByUserIdWithProduct` DTO projection 한 번으로 가져온다. 삭제 상품은 제외하고 최근 추가순이다. 금액은 현재 단가 × 수량이다.

## 알아둘 구현 방식
- 이미 담긴 상품을 동시에 담으면 lost update가 생길 수 있다. 조회 후 엔티티를 더하는 방식이고 락이 없기 때문이다. 고칠 때는 조건부 UPDATE(`quantity = quantity + ?`)나 버전 컬럼 중에서 고른다.
- 상품 삭제로 장바구니 항목이 지워지는 것은 bulk delete라서 영속성 컨텍스트에 반영되지 않는다.

## 테스트 기준
이 패키지를 바꾸면 단위 테스트(`CartItemTest`, `CartItemServiceTest`)로 아래를 확인한다.
- 새 상품 담기, 기존 상품 합산, 사용자·상품 없음, 유니크 충돌 → `7001`
- 썸네일 있음·없음 목록
- 수량 변경·삭제의 성공과 없음
- 경계 사례: 수량 0이나 음수, 삭제된 상품 담기 → 404 `2000`
