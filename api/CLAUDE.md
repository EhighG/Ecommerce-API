# api — Spring Boot 애플리케이션

## 담당 범위
- REST API 서버 전체다. 도메인 패키지는 `src/main/java/com/ecommerce/api/`의 auth, user, product, inventory, media, cartitem, order, coupon, review, idempotency, common이다.
- 프로필 설정(`src/main/resources/application*.yaml`), 배포 이미지(`Dockerfile`), 테스트(`src/test`)도 여기에 있다.
- 현재 스키마 전체 DDL(`db/schema.sql`)과 로컬 수평 확장 예시(`infra/scaleout-local/`, `.env` 제외)도 여기에 있다.
- 담당하지 않는 것: 부하테스트 스크립트(`../k6`), 부하 발생기 자동화(`../infra`), 배포 워크플로(`../.github`), 로컬 전용 파일(`compose.yaml`, `infra/gcp` 키, `application-secret.yaml`, `.env`). 로컬 전용 파일은 커밋하지 않으며, 수정·생성은 사용자가 요청할 때만 한다.

## 항상 지켜야 할 것
- `./gradlew test`가 통과해야 한다. Docker가 필요하고, `reproduction` 태그는 제외된다.
- 모든 프로필의 `ddl-auto`는 `validate`다. `create`나 `update`를 커밋하지 않는다. 테스트만 `create-drop`이다.
- JDBC URL의 `rewriteBatchedStatements=true`를 유지한다.
- `ObjectMapper`는 `tools.jackson.databind.ObjectMapper`(Jackson 3)를 쓴다.
- 요청 사이의 공유 상태를 static 필드나 로컬 캐시에 두지 않는다. 인스턴스가 여러 대다.

## 공통 구현 방식
- 요청을 처리하는 순서:
  1. 컨트롤러가 `@AuthenticationPrincipal CustomUserDetails`로 사용자 ID와 역할을 얻는다.
  2. 요청 DTO(record, `@Valid`)와 함께 서비스를 호출한다.
  3. 서비스에서 소유자와 상태를 검사하고 엔티티 메서드를 호출한다.
  4. 응답 DTO(record)로 변환한다.
- 업무 오류는 `throw new AppException(ErrorCode.X[, "구체적 한국어 메시지"])`로 던진다. 전역 핸들러가 `{code, message}`로 바꾼다.
- 요청 record는 compact constructor에서 기본값만 채우고, 필드 사이 조건은 `@AssertTrue` 메서드로 검사한다(`docs/standards.md`의 "입출력 계약 패턴").
- 목록 조회는 fetch join 또는 DTO projection(JPQL `new …`, Querydsl `Projections.constructor`)으로 한다. 목록마다 N+1이 생기지 않게 한다.
- 썸네일이나 이미지 URL은 저장된 object key를 `MediaService.resolveUrl`로 바꿔서 만든다. URL을 DB에 저장하지 않는다.

## 테스트 방식
- **단위 테스트**
  - Mockito와 `support/UnitTestFixtures`(`buyer()`, `seller()`, `product()`, `cartItemWithId()` 등)를 쓴다.
  - ID가 필요하면 `ReflectionTestUtils`로 넣는다.
  - 메서드 이름은 `동작_조건_결과` 또는 `given…_when…_then…` 형식이고, `@DisplayName`은 한국어로 쓴다.
- **통합 테스트**: `support/OrderServiceIntegrationTestSupport`를 상속한다.
  - MySQL 8.4 컨테이너를 JVM당 한 번 띄운다.
  - 테스트 클래스는 트랜잭션 밖(`NOT_SUPPORTED`)에서 실행한다. 준비 데이터는 `tx(...)`로 커밋한다.
  - 매 테스트 전에 `CLEANUP_TABLES`를 비운다. 새 테이블을 만들면 이 목록에 추가한다.
  - 필요한 빈만 `OrderServiceIntegrationTestConfig`에 `@Import`한다. Redis, GCS, 비밀번호 인코더 등은 mock이나 가짜로 대체한다.
- **반드시 테스트할 것**
  - 동시성·트랜잭션 경계가 있는 코드(주문, 취소, 멱등, 재고, 쿠폰 사용·복구)는 실제 MySQL 통합 테스트로 검증한다.
  - 새 엔드포인트는 비로그인(401), 다른 역할(403), 다른 소유자(404 또는 403)를 확인한다.
- 테스트 DB에는 FULLTEXT 인덱스가 없다. 키워드 검색을 테스트하려면 준비 단계에서 인덱스 DDL을 실행한다.
