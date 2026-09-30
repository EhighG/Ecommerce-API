# review — 리뷰와 평점 통계

## 담당 범위
- 담당하는 것:
  - `Review`(유니크 `writer_id + product_id`, 삭제 없음)
  - `Rating`(halfStars 0~10을 담는 값 객체)
  - 작성, 수정, 상품 기준·작성자 기준 목록, 프로필용 최근 5개와 개수
- 담당하지 않는 것:
  - 평점 통계 컬럼과 SQL(`ProductStatRepository.addReview` / `changeReviewRating` 호출만 함)
  - 구매확정 판정(`OrderItemRepository.existsByOrderBuyerIdAndProductIdAndStatus` 호출만 함)
- 리뷰 삭제 기능은 만들지 않는다(규칙).

## 항상 지켜야 할 것
- 작성 조건은 이 순서로 검사한다:
  1. 삭제되지 않은 상품(`2000`)
  2. 구매자의 `PURCHASE_CONFIRMED` 주문항목이 있음(`6001`, 403)
  3. 기존 리뷰가 없음(`6002`, 403)
- 작성하면 `addReview(productId, halfStars)`, 수정하면 `changeReviewRating(productId, 새 값 − 이전 값)`을 **같은 트랜잭션에서** 실행한다. 변화량이 0이면 통계를 건드리지 않는다. 평균은 SQL에서 `(합) / 개수 / 2.0`으로 계산한다.
- 수정은 작성자만 할 수 있다. 다른 사람이면 `REVIEW_WRITER_MISMATCH`(404로 숨김)다. 삭제된 상품의 리뷰는 수정할 수 없다(`2004`).
- 수정 요청에서 `content`가 `null`이면 내용을 `null`로 덮어쓴다(부분 수정이 아니다).
- 탈퇴한 작성자는 응답에 `"삭제된 사용자입니다"`로 표시한다(`ProductReviewListRes.UserSummary`).

## 알아둘 구현 방식
- 작성과 수정의 유니크 경쟁: 같은 사용자가 동시에 두 번 작성하면 `findByProductIdAndWriterId` 검사를 둘 다 통과할 수 있다. 그러면 유니크 제약 위반이 처리되지 않은 예외(500)가 된다.
- 같은 리뷰를 동시에 수정하면 둘 다 같은 이전 별점으로 변화량을 계산해 평점 합계가 틀어진다(`docs/tracking/findings/product.md`).
- `isUpdated`는 `updatedAt != createdAt`으로 판단한다. 감사 시각은 생성할 때 같은 값으로 들어간다.
- 목록 조회는 `@EntityGraph`로 작성자나 상품·썸네일을 함께 가져온다. 정렬은 `createdAt DESC`이다. 페이지 크기는 다른 목록처럼 20/50/100만 허용한다(`docs/standards.md`).

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 구매확정 이력이 없으면 403, 중복 작성은 403.
- 작성 후 통계가 맞는지(개수 +1, 합, 평균).
- 별점 수정 시 변화량만 반영되는지.
- 남의 리뷰 수정은 404, 삭제된 상품의 리뷰 수정은 400.
- 탈퇴한 작성자는 닉네임이 치환되는지.
- 경계값: halfStars 0, 10, −1, 11.
