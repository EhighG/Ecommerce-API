# product — 상품, 카테고리, 이미지 연결, 통계, 조회수

상품·카테고리·검색·조회수 규칙은 `docs/business-rules.md`의 "상품"과 "상품 목록·검색"에 있다.

## 담당하지 않는 것
- 재고 행의 규칙(inventory). 등록 시 생성만 호출한다.
- 업로드 이미지의 저장과 URL 계산(media)
- 주문 수 증감 시점(order가 호출)
- 평점 반영 시점(review가 호출)
- 주문항목의 상품 스냅샷(order의 `ProductSnapshot`)

## 항상 지켜야 할 것
- 상품 등록은 한 트랜잭션에서 `Product` + `ProductStat` + `Inventory`(+ 이미지 연결)를 만든다. 목록·상세 조회는 셋을 inner join한다.
- 쓰기 대상과 사용자에게 보이는 조회는 `findByIdAndDeletedFalse` 또는 `getProduct()`를 쓴다. `findById`는 리뷰처럼 삭제된 상품도 보여야 하는 경우에만 쓴다(숨김 삭제 규칙은 `docs/standards.md`).
- 상품 삭제(`ProductDeletionService.delete`)의 순서:
  1. 장바구니 bulk delete
  2. 썸네일 null로 바꾸고 `flush`
  3. `ProductImage` 삭제
  4. 업로드 이미지 detach
  5. 상품 숨김 삭제

  썸네일 FK를 먼저 풀지 않으면 `ProductImage` 삭제가 FK 위반이 된다. 판매자 탈퇴용 `deleteAllBySeller`는 같은 작업을 bulk JPQL로 한다.
- 이미지를 붙일 때 남이 올린 이미지는 없는 이미지와 같게 404 `8003`으로 응답한다(`docs/security.md`의 404/403 기준). `ProductImage` 생성자가 `markAttached()`를 호출한다.
- 통계 갱신은 SQL 한 문장으로 한다.
  - 주문 수: `ProductStatJdbcRepository.increaseOrderCounts`(배치, `product_id` 오름차순) 또는 `ProductStatRepository.increaseOrderItemCount`
  - 평점: `addReview` / `changeReviewRating`. `rating_sum`, `review_count`, `rating_avg`를 한 문장에서 갱신하므로 동시 리뷰 작성에도 합계가 맞는다.
  - **SET 절의 순서가 계산 결과를 바꾼다.** MySQL은 SET 대입을 왼쪽부터 차례로 적용하고, 뒤의 식은 앞에서 바뀐 값을 본다. 그래서 `rating_avg`를 **맨 앞에** 두어 갱신 전 합계와 개수로 계산한다. 다른 대입 뒤로 옮기면 평균이 조용히 틀어진다.
  - 엔티티를 읽어서 더한 뒤 저장하지 않는다. 동시 요청에서 값이 유실된다.
  - 결과가 1행이 아니면 `PRODUCT_STAT_NOT_FOUND`다.
- 검색 키워드는 특수문자를 공백으로 바꾸고, 각 토큰에 `+`를 붙여 BOOLEAN MODE AND 검색을 한다. 남는 토큰이 없으면 `FALSE` 조건(0건)이다. 키워드가 공백뿐이면 키워드 조건을 걸지 않는다.
- 목록 정렬은 항상 `(선택한 기준, product.id DESC)`다. 인덱스 `idx_product_deleted_created_id`, `idx_product_category_deleted_created_id`는 기본 정렬(등록일)과 카테고리 필터를 위한 것이다. 쿼리 모양을 바꾸면 `EXPLAIN`으로 인덱스를 타는지 확인한다.

## 알아둘 구현 방식
- 조회수
  - `increase`: Redis `INCR product:view-count:delta:{id}` + `SADD product:view-count:dirty {id}`
  - `flushToDb`(30초, 모든 인스턴스): `SPOP` batchSize만큼 꺼냄 → `GETDEL` 증가분 → JDBC 배치 반영. 이것을 최대 maxBatchesPerRun번 반복한다.
  - DB 반영이 예외로 실패하면 증가분을 Redis에 되돌리고 이번 주기를 끝낸다. 하지만 꺼낸 뒤 반영 전에 프로세스가 죽으면 그 증가분은 사라지고, 증가분 키만 남고 dirty set에서 빠진 상품은 다음 조회가 있을 때까지 반영되지 않는다(허용된 손실).

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 등록 시 통계·재고 행 생성
- 이미지 연결 오류 3종
- 썸네일 선정(순서 1이 없으면 null)
- 삭제 전파: 장바구니 제거, 이미지 detach, 주문·리뷰 유지
- 판매자 불일치 → 403
- 검색 AND 조건, 특수문자만 입력하면 0건. FULLTEXT DDL이 필요하다(`docs/engineering-notes.md`).
- 정렬 동률 처리
- 조회수 반영 실패 시 증가분 복구
- 평점 통계: 작성·수정 후 합계·개수·평균(SET 절 순서)
