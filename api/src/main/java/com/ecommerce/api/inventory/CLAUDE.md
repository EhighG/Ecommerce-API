# inventory — 상품별 재고

재고 규칙은 `docs/business-rules.md`의 "재고"에, 락 순서 규칙은 `docs/standards.md`의 "트랜잭션·동시성"에 있다.

## 담당하지 않는 것
- 주문 흐름에서 언제 차감하고 복구하는지(order가 호출)
- 상품 삭제 여부의 원본(product)
- 재고 표시(목록과 상세 조회는 product 쿼리가 조인해서 읽음)

## 항상 지켜야 할 것
- 엔티티의 `update`와 `adjust`는 결과가 음수면 `INVALID_INPUT`을 던진다. 주문 차감은 SQL 조건 `quantity >= ?`로 보장한다.
- 주문 차감은 `InventoryJdbcRepository.deductAll`만 쓴다.
  - 상품별 수량을 합친 뒤 `product_id` 오름차순으로 배치 UPDATE한다.
  - 결과가 행마다 `1`이 아니면 `INSUFFICIENT_INVENTORY`를 던져 롤백시킨다.
  - 차감 전에 조회하는 단계를 다시 넣지 않는다(의도적으로 없앴다, 결정 기록 0003).
- 복구와 판매자 수정은 `findByProductIdForUpdate`(비관적 락) 후 엔티티 메서드로 한다.
- 판매자 수정의 검사 순서: 재고 행 없음 → `NO_INVENTORY_FOR_PRODUCT`(500), 삭제 상품 → `DELETED_PRODUCT`(400), 판매자 불일치 → `SELLER_NOT_MATCHED`(403). 재고 행이 없는 것은 데이터 이상이라 500을 유지한다.

## 알아둘 구현 방식
- 결과 개수 검사 코드에 `SUCCESS_NO_INFO`(−2) 처리가 없다. 운영 드라이버 옵션(`rewriteBatchedStatements=true`)에서는 UPDATE 개수가 정상으로 돌아오는 것을 부하테스트로 확인했다.

## 테스트 기준
재고 차감·복구·수정을 바꾸면 아래를 테스트한다.
- 재고가 딱 맞을 때(= 주문 수량) 성공하고 0이 되는지 확인한다.
- 1 부족하면 전체 롤백되는지 확인한다.
- 같은 상품이 여러 항목이면 합계로 판정하는지 확인한다. 현재 API로는 한 주문에 같은 상품이 두 줄 들어올 수 없어서 서비스 단위로만 확인할 수 있다.
- 취소하면 수량이 원래대로 돌아오는지 확인한다.
- 동시 주문으로 초과 판매가 없는지(최종 재고 ≥ 0, 성공 건수 × 수량 = 차감량) 확인한다.
