# review — 리뷰와 평점 통계

리뷰 규칙(작성 자격, 수정, 삭제 불가, 표시)은 `docs/business-rules.md`의 "리뷰"에 있다.

## 담당하지 않는 것
- 평점 통계 컬럼과 SQL(`ProductStatRepository.addReview` / `changeReviewRating` 호출만 함. 주의점은 product 모듈 문서)
- 구매확정 판정(`OrderItemRepository.existsByOrderBuyerIdAndProductIdAndStatus` 호출만 함)

## 항상 지켜야 할 것
- 작성 조건은 이 순서로 검사한다:
  1. 삭제되지 않은 상품(`2000`)
  2. 구매자의 `PURCHASE_CONFIRMED` 주문항목이 있음(`6001`, 403)
  3. 기존 리뷰가 없음(`6002`, 403)
- 작성하면 `addReview(productId, halfStars)`, 수정하면 `changeReviewRating(productId, 새 값 − 이전 값)`을 **같은 트랜잭션에서** 실행한다. 변화량이 0이면 통계를 건드리지 않는다.
- 수정은 작성자가 아니면 `REVIEW_WRITER_MISMATCH`(404로 숨김), 삭제된 상품의 리뷰면 `2004`다.

## 알아둘 구현 방식
- 작성과 수정의 유니크 경쟁: 같은 사용자가 동시에 두 번 작성하면 `findByProductIdAndWriterId` 검사를 둘 다 통과할 수 있다. 그러면 유니크 제약 위반이 처리되지 않은 예외(500)가 된다.
- 같은 리뷰를 동시에 수정하면 평점 합계가 틀어진다(`docs/tracking/findings/product.md`).
- `isUpdated`의 판단 근거인 감사 시각은 생성할 때 `createdAt`과 `updatedAt`에 같은 값으로 들어간다.

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 구매확정 이력이 없으면 403, 중복 작성은 403.
- 작성 후 통계가 맞는지(개수 +1, 합, 평균).
- 별점 수정 시 변화량만 반영되는지.
- 남의 리뷰 수정은 404, 삭제된 상품의 리뷰 수정은 400.
- 탈퇴한 작성자는 닉네임이 치환되는지.
- 경계값: halfStars 0, 10, −1, 11.
