# api — Spring Boot 애플리케이션

계층, 트랜잭션, 엔티티, 입출력, 설정 규칙은 `docs/standards.md`에 있다. 여기에는 그 밖의 공통 구현 방식과 테스트 방식만 적는다.

## 담당하지 않는 것
- 부하테스트 스크립트(`../k6`), 부하 발생기 자동화(`../infra`), 배포 워크플로(`../.github`)
- 로컬 전용 파일(`compose.yaml`, `infra/gcp` 키, `application-secret.yaml`, `.env`). 커밋하지 않으며, 수정·생성은 사용자가 요청할 때만 한다.

## 공통 구현 방식
- 목록 조회는 fetch join 또는 DTO projection(JPQL `new …`, Querydsl `Projections.constructor`)으로 한다. 목록마다 N+1이 생기지 않게 한다.
- 썸네일이나 이미지 URL은 저장된 object key를 `MediaService.resolveUrl`로 바꿔서 만든다. URL을 DB에 저장하지 않는다. 버킷 공개 설정이나 기본 URL이 바뀌어도 데이터를 옮길 필요가 없게 하기 위해서다.

## 테스트 방식
- **단위 테스트**
  - Mockito와 `support/UnitTestFixtures`(`buyer()`, `seller()`, `product()`, `cartItemWithId()` 등)를 쓴다.
  - ID가 필요하면 `ReflectionTestUtils`로 넣는다.
  - 메서드 이름은 `동작_조건_결과` 또는 `given…_when…_then…` 형식이고, `@DisplayName`은 한국어로 쓴다.
- **통합 테스트**: `support/OrderServiceIntegrationTestSupport`를 상속한다.
  - MySQL 8.4 컨테이너를 JVM당 한 번 띄운다.
  - 테스트 클래스는 트랜잭션 밖(`NOT_SUPPORTED`)에서 실행한다. 준비 데이터는 `tx(...)`로 커밋한다.
  - 매 테스트 전에 `CLEANUP_TABLES`의 테이블을 비운다.
  - 필요한 빈만 `OrderServiceIntegrationTestConfig`에 `@Import`한다. Redis, GCS, 비밀번호 인코더 등은 mock이나 가짜로 대체한다.
- 어떤 코드를 실제 MySQL 통합 테스트로 검증해야 하는지는 `docs/standards.md`의 "검증 게이트"에, 새 엔드포인트의 확인 항목은 `docs/engineering-notes.md`의 "API 엔드포인트를 추가할 때"에 있다.
