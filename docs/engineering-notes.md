# 엔지니어링 노트

여러 모듈이나 운영에 걸친 함정과 반복 작업 체크리스트를 적는다. 한 모듈 안의 함정은 그 모듈의 `CLAUDE.md`에, 아직 고치지 않은 결함은 `docs/tracking/findings/`에 있다.

## 함정과 해결

### 새 DB나 테스트 DB에서 키워드 검색이 500을 낸다
- **증상**: `GET /api/products?keyword=…`가 500(`9999`)을 내고, 로그에 FULLTEXT 인덱스를 찾을 수 없다는 SQL 오류가 남는다. 키워드가 없는 목록 조회는 정상이다.
- **원인**
  - 검색 쿼리는 `MATCH(name, description) AGAINST(? IN BOOLEAN MODE)`를 쓴다. 그런데 이 인덱스는 엔티티 어노테이션에 없다. `api/db/schema.sql`로 만든 DB에만 있다.
  - `create-drop`으로 스키마를 만드는 테스트 DB나, 엔티티에서 만든 DB에는 인덱스가 없다.
- **해결**
  - 인덱스를 만든다: `ALTER TABLE product ADD FULLTEXT INDEX ft_product_name_description (name, description) WITH PARSER ngram;`
  - MySQL 서버 옵션도 맞아야 한다. 필요한 옵션과 확인 쿼리는 `api/db/schema.sql` 머리에 있다. 토큰 크기가 다르면 같은 검색어라도 결과가 달라진다.
  - 키워드 검색을 테스트하려면 테스트 준비 단계에서 이 DDL을 실행해야 한다.
- **확인**: `EXPLAIN`에서 `product` 접근 방식이 `fulltext`로 나오는지 본다.

### 주문 생성과 취소가 동시에 돌면 데드락이 난다
- **증상**: 부하 중에 `Deadlock found when trying to get lock` 오류가 나고 주문 요청이 500으로 실패한다.
- **원인**: 주문 생성과 취소가 `inventory`와 `product_stat` 행을 서로 다른 순서로 잠갔다. 또 한 트랜잭션 안에서 여러 상품을 임의 순서로 갱신했다.
- **해결**: 두 경로가 같은 락 순서를 따르게 했다. 규칙은 `standards.md`의 "트랜잭션·동시성"에 있다.
- **확인**: 주문·취소를 섞은 k6 시나리오를 돌리고 MySQL `SHOW ENGINE INNODB STATUS`의 LATEST DETECTED DEADLOCK이 새로 생기지 않는지 본다.

### 커넥션 대기가 쌓인다고 풀 크기를 늘리면 더 느려진다
- **증상**: HikariCP pending이 수백까지 쌓이고, 획득 대기가 1초를 넘는다.
- **원인**: 병목은 API VM의 CPU였다. 요청 하나가 DB를 많이 호출할수록 JPA·JDBC 처리 비용이 커진다.
  - 풀을 10에서 30으로 늘리자 CPU 경쟁이 더 심해져 주문 생성 응답이 더 느려졌다.
- **해결**: 요청당 DB 호출 수를 줄이고([결정 기록 0003](adr/0003-order-query-batching.md)) 수평 확장했다([결정 기록 0004](adr/0004-horizontal-scale-out.md)). 측정값은 [사례 1](cases/01-order-query-batching.md)과 [사례 3](cases/03-horizontal-scale-out.md)에 있다.
- **확인**: 풀 크기를 바꾸기 전에 같은 시간대의 API VM CPU 사용률과 Tomcat busy thread를 먼저 본다.

### JDBC 배치가 운영과 테스트에서 다르게 동작한다
- **증상**: 테스트에서는 괜찮은데 운영 성능이 다르게 나오거나, 그 반대다.
- **원인**
  - 운영 JDBC URL에는 `rewriteBatchedStatements=true`가 붙어 있어서, 배치가 DB 왕복 한 번으로 묶인다.
  - Testcontainers가 만드는 테스트 URL에는 이 옵션이 없다.
  - 또 `hibernate.jdbc.batch_size` / `order_updates` 설정은 `local`과 `dev` 프로필에만 있고 `loadtest` 프로필에는 없다.
- **해결**
  - JDBC URL 옵션을 지우지 않는다.
  - Hibernate 배치 설정을 바꿀 때는 세 프로필 파일을 모두 확인한다.
  - 배치 결과 개수를 검사하는 로직(각 행 `== 1`)을 바꾸면 운영 옵션을 켠 상태로 검증한다.

### JDBC·벌크 UPDATE·DELETE 뒤에 엔티티 값이 옛 값이다
- **증상**: 같은 트랜잭션에서 재고를 차감한 뒤 `Inventory` 엔티티를 읽으면 차감 전 수량이 보인다. 상품 삭제로 지운 장바구니 항목이 같은 트랜잭션의 영속성 컨텍스트에는 남아 있다.
- **원인**: `JdbcTemplate`과 `@Modifying` JPQL은 영속성 컨텍스트를 거치지 않는다. 재고 차감·복구, 통계 갱신, 상품 삭제 전파가 이 방식을 쓴다.
- **대응**: 직접 UPDATE·DELETE 뒤에 같은 트랜잭션에서 그 엔티티 값으로 판단하지 않는다. 꼭 필요하면 다시 조회하되, 1차 캐시를 비우고 조회해야 한다.

### 로컬에서 GCP 자격 증명 오류로 서버가 뜨지 않는다
- **증상**: 기동할 때 `GcsConfig`에서 Application Default Credentials를 찾을 수 없다는 예외가 난다.
- **원인**
  - GCS 클라이언트 빈을 기동 시점에 만들기 때문에, 이미지 기능을 쓰지 않더라도 자격 증명이 있어야 한다.
  - `application-secret.yaml`에 적은 `GOOGLE_APPLICATION_CREDENTIALS`는 Spring 설정 값일 뿐이다. Google 라이브러리는 OS 환경변수나 gcloud 기본 자격 증명만 읽는다.
- **해결**: 실행 환경에 OS 환경변수 `GOOGLE_APPLICATION_CREDENTIALS=<키 파일 경로>`를 설정한다(IDE 실행 구성 포함). 또는 `gcloud auth application-default login`을 실행한다.

### Redis를 잃은 직후 쿠폰 발급이 거절된다
- **증상**: Redis를 재시작한 직후 열린 이벤트인데도 발급이 400(`7503`)으로 거절된다.
- **원인과 복구**: 쿠폰 발급은 Redis의 이벤트 캐시가 있어야만 진행된다. 30초 주기 스케줄러가 캐시를 다시 올리면 발급이 돌아온다. 다시 올릴 때의 수량 계산과, 이미 받은 사람이 다시 요청할 때의 응답은 `coupon/CLAUDE.md`에 있다.
- **알아둘 점**: Redis가 비지 않고 과거 상태로 되살아나면 재적재가 일어나지 않아 초과 발급될 수 있다. 보상하지 못하는 경우도 있다(`docs/tracking/findings/coupon.md`).

### 환경변수를 빠뜨리면 빈 값이 아니라 `${…}` 문자열이 들어간다
- **증상**: 환경변수를 설정하지 않았는데 서버가 정상 기동한다. 이미지 경로가 `${GCS_UPLOAD_PREFIX}/20260927/…`처럼 만들어진다.
- **원인**: Spring은 해석하지 못한 자리표시자를 오류로 보지 않고 문자열 그대로 `@ConfigurationProperties`에 넣는다. 그래서 "비어 있으면 실패" 검사를 통과한다.
- **대응**: 필수 값은 빈 기본값 `${ENV:}`로 받고 빈 값 검사를 둔다. 누락이 빈 문자열이 되어 검사에 걸린다. 부하테스트 비밀값(`LOADTEST_AUTH_SECRET`)이 이 방식이다. 아직 기본값이 없는 필수 자리표시자(`GCS_UPLOAD_PREFIX` 등)는 같은 문제를 갖고 있다.
- **확인**: 기능의 결과로 본다. 업로드 URL의 객체 경로 접두어가 기대한 값인지 확인한다. 자리표시자 누락을 막는 테스트는 실제 `application-*.yaml`을 읽어 환경변수 없이 기동이 실패하는지 본다(`LoadTestAuthenticationFilterStartupTest`).

### 배포 워크플로의 테스트와 Docker 이미지 빌드가 서로 다른 Gradle을 쓴다
- **증상**: 배포 워크플로의 테스트 단계는 통과했는데 이미지 빌드에서만 실패할 수 있다(또는 그 반대).
- **원인**: 테스트 단계는 `./gradlew`(9.4.1)를 쓰고, `api/Dockerfile`은 `gradle:8.14.3` 이미지의 `gradle bootJar`를 쓴다. PR 테스트 워크플로도 `./gradlew`만 쓰므로, 이미지 빌드 실패는 배포할 때에야 드러난다.
- **대응**: 빌드 스크립트나 플러그인 버전을 올릴 때는 `docker build`도 로컬에서 돌려 본다. 이 이미지는 외부로 내보내지 않는다(`security.md`의 "자격 증명 관리").

## 반복 작업 체크리스트

### 엔티티나 테이블을 바꿀 때
1. 엔티티를 수정하고, 같은 변경의 DDL(ALTER/CREATE)을 작성한다. `api/db/schema.sql`에도 같은 변경을 반영한다.
2. `./gradlew test`를 실행한다. 테스트는 `create-drop`이라 DDL 누락을 잡지 못한다.
3. 로컬 MySQL에 DDL을 적용하고 `local` 프로필로 기동한다. `validate`가 통과하면 매핑과 DDL이 일치한다는 뜻이다.
4. 새 테이블이면 `OrderServiceIntegrationTestSupport.CLEANUP_TABLES`에 추가한다. 정리할 때 FK 검사를 끄므로 순서는 상관없다. 빠뜨리면 테스트끼리 데이터가 섞인다.
5. 배포 전에 클라우드 DB에 같은 DDL을 적용한다. 적용하지 않고 배포하면 새 인스턴스가 기동하지 못하고, 전체 교체 방식이라 서비스가 멈춘다.

### API 엔드포인트를 추가할 때
1. `SecurityConfig`의 역할별 matcher 목록에 경로를 넣는다. 넣지 않으면 "로그인한 사용자 누구나"로 열린다. 빠뜨리면 `SecurityMatcherCoverageTest`가 실패한다.
2. 서비스에서 소유자를 검사하고, 404와 403 기준을 따른다. 소유·조건 검사가 새로 생기면 `security.md` 인가 표에 행을 추가한다.
3. 필요한 오류 코드를 `ErrorCode`의 도메인 대역에 추가한다.
4. 컨트롤러 메서드에 설명과 분기할 오류를 달고 API 명세를 다시 만든다. 방법은 `api/CLAUDE.md`의 "공통 구현 방식"에 있다.
5. 확인: 비로그인(401), 다른 역할(403), 다른 소유자(사용자 소유 자원은 404, 공개 자원은 403)로 호출해 본다.

### 스케줄 작업을 추가할 때
1. 인스턴스 2대가 같은 순간에 실행해도 결과가 같은지 먼저 설계한다(방법은 `standards.md`의 "트랜잭션·동시성").
2. 실패하면 로그를 남기고 다음 주기에 다시 시도할 수 있게 한다. 예외가 스케줄러 스레드를 죽이면 안 된다.
3. 확인: 로컬에서 인스턴스 2대를 띄우고(`api/infra/scaleout-local/` 예시) 결과가 중복되지 않는지 본다.

### 부하테스트를 돌릴 때
1. 대상 서버와 데이터를 준비한다. 절차는 `operations.md`의 "부하테스트"에 있다. 후보 SQL은 데이터를 넣으므로 부하테스트 전용 DB에서만 돌린다.
2. 워밍업 구간(기본 3분)을 반드시 둔다. JVM 워밍업 없이 측정한 값은 비교에 쓰지 않는다.
3. 확인: 결과를 비교할 때는 부하율, 구간 구성, 데이터, DB 인스턴스 사양이 같은지 먼저 맞춘다. DB 인스턴스만 바꿔도 결과가 수십 배 달라진 적이 있다.
