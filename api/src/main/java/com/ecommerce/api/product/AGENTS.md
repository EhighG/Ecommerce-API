# product — 상품, 카테고리, 이미지 연결, 통계, 조회수

## 담당 범위
- 담당하는 것:
  - `Product`(숨김 삭제), `ProductCategory`, `ProductImage`(상품–업로드 이미지 연결, 표시 순서)
  - `ProductStat`(주문 수, 조회수, 리뷰 수, 평점 합과 평균. 상품과 1:1이고 PK를 공유)
  - 검색(`ProductQueryRepositoryImpl`, Querydsl + FULLTEXT)
  - 조회수 버퍼·반영(`ProductViewCountService`)
  - 상품 삭제 전파(`ProductDeletionService`)
- 담당하지 않는 것:
  - 재고 행의 규칙(inventory). 등록 시 생성만 호출한다.
  - 업로드 이미지의 저장과 URL 계산(media)
  - 주문 수 증감 시점(order가 호출)
  - 평점 반영 시점(review가 호출)
  - 주문항목의 상품 스냅샷(order의 `ProductSnapshot`)

## 항상 지켜야 할 것
- 상품 등록은 한 트랜잭션에서 `Product` + `ProductStat` + `Inventory`(+ 이미지 연결)를 만든다. 셋 중 하나라도 빠진 상품이 생기면, 이후 목록·상세 조인(inner join)에서 사라지거나 500이 난다.
- `Product`에는 `@SQLDelete`가 있다. `productRepository.delete()`는 `deleted = true`로 바뀐다.
  - 조회 필터가 없으므로, 쓰기 대상과 사용자에게 보이는 조회는 `findByIdAndDeletedFalse` 또는 `getProduct()`를 쓴다.
  - `findById`는 리뷰처럼 삭제된 상품도 보여야 하는 경우에만 쓴다.
- 상품 삭제(`ProductDeletionService.delete`)의 순서:
  1. 장바구니 bulk delete
  2. 썸네일 null로 바꾸고 `flush`
  3. `ProductImage` 삭제
  4. 업로드 이미지 detach
  5. 상품 숨김 삭제
  
  썸네일 FK를 먼저 풀지 않으면 `ProductImage` 삭제가 FK 위반이 된다. 판매자 탈퇴용 `deleteAllBySeller`는 같은 작업을 bulk JPQL로 한다.
- 이미지 연결의 검사 순서: 요청한 ID가 모두 있는지(`8003`) → 업로드한 사람 = 판매자(`8002`) → 이미 붙은 이미지 아님(`8004`). `ProductImage` 생성자가 `markAttached()`를 호출한다. 표시 순서 1인 이미지가 썸네일이 된다.
- 통계 갱신은 SQL 한 문장으로 한다.
  - 주문 수: `ProductStatJdbcRepository.increaseOrderCounts`(배치, `product_id` 오름차순) 또는 `ProductStatRepository.increaseOrderItemCount`
  - 평점: `addReview` / `changeReviewRating`
  - 엔티티를 읽어서 더한 뒤 저장하지 않는다. 동시 요청에서 값이 유실된다.
  - 결과가 1행이 아니면 `PRODUCT_STAT_NOT_FOUND`다.
- 검색 키워드는 `+ - < > ( ) ~ * " @`를 공백으로 바꾸고, 각 토큰에 `+`를 붙여 BOOLEAN MODE AND 검색을 한다. 남는 토큰이 없으면 `FALSE` 조건(0건)이다.
- 목록 정렬은 항상 `(선택한 기준, product.id DESC)`다. 인덱스 `idx_product_deleted_created_id`, `idx_product_category_deleted_created_id`는 기본 정렬(등록일)과 카테고리 필터를 위한 것이다. 쿼리 모양을 바꾸면 `EXPLAIN`으로 인덱스를 타는지 확인한다.

## 알아둘 구현 방식
- FULLTEXT 인덱스 `ft_product_name_description (name, description) WITH PARSER ngram`은 엔티티에 선언되어 있지 않다. 운영 DB에만 있고, 테스트 DB(`create-drop`)에는 없다.
- 조회수
  - `increase`: Redis `INCR product:view-count:delta:{id}` + `SADD product:view-count:dirty {id}`
  - `flushToDb`(30초, 모든 인스턴스): `SPOP` batchSize만큼 꺼냄 → `GETDEL` 증가분 → JDBC 배치 반영. 이것을 최대 maxBatchesPerRun번 반복한다.
  - DB 반영이 예외로 실패하면 증가분을 Redis에 되돌리고 이번 주기를 끝낸다.
- 카테고리 삭제는 현재 사용 여부를 검사하지 않는다. 상품이 걸려 있으면 FK 오류로 500이 난다. 사용 중이면 거절(400/409, 전용 코드)하는 것이 규칙이다.
- 상품 수정에서 삭제된 상품은 `getProduct`에서 걸러져 404(`2000`)다. 재고 수정은 400(`2004`)으로, 응답이 다르다.

## 테스트 기준(현재 없음)
- 등록 시 통계·재고 행 생성
- 이미지 연결 오류 3종
- 썸네일 선정(순서 1이 없으면 null)
- 삭제 전파: 장바구니 제거, 이미지 detach, 주문·리뷰 유지
- 판매자 불일치 → 403
- 검색 AND 조건, 특수문자만 입력하면 0건. FULLTEXT DDL이 필요하다.
- 정렬 동률 처리
- 조회수 반영 실패 시 증가분 복구
