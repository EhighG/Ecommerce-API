# 규칙

아래 규칙은 어기면 빌드 실패, 테스트 실패, 서버 기동 실패, 또는 데이터 정합성 붕괴로 이어진다.

## 빌드·의존성

- 빌드와 테스트는 `api/`의 Gradle Wrapper(`./gradlew`, Gradle 9.4.1)로만 실행한다. 로컬에 설치된 Gradle을 쓰지 않는다.
- Java 21 toolchain을 쓴다.
- Spring Boot가 관리하는 라이브러리는 버전을 적지 않는다(BOM이 정함). 예외는 세 가지다. Querydsl 5.1.0(`:jakarta`)과 GCP `libraries-bom`은 Boot BOM에 없어서 적는다. Testcontainers 모듈은 BOM 관리 버전(2.0.4)을 의도적으로 2.0.5로 올려 적었다. BOM 버전을 덮어쓰는 새 의존성은 이유를 커밋 메시지에 남긴다.
- JSON 처리 객체는 Jackson 3의 `tools.jackson.databind.ObjectMapper`를 주입받는다. Spring Boot 4는 Jackson 3 객체만 자동으로 등록하므로, `com.fasterxml.jackson.databind.ObjectMapper`를 주입하면 빈을 찾지 못해 기동에 실패한다. 반면 DTO 어노테이션(`@JsonInclude` 등)은 `com.fasterxml.jackson.annotation` 패키지를 그대로 쓴다.
- Querydsl Q클래스는 annotation processor가 빌드할 때 만든다. 생성된 파일을 커밋하지 않는다.

## 검증 게이트

- 병합 전 `cd api && ./gradlew test`가 통과해야 한다. 배포 워크플로도 이미지를 빌드하기 전에 `./gradlew cleanTest test`를 실행하고, 실패하면 배포하지 않는다.
- 통합 테스트는 Testcontainers(MySQL 8.4)를 쓰므로 Docker가 떠 있어야 한다. Docker가 없어서 테스트를 건너뛰었다면 "통과"가 아니다.
- `@Tag("reproduction")`이 붙은 테스트는 기본 실행에서 빠진다. 버그를 재현하는 테스트나 오래 걸리는 테스트에만 붙인다.
- 주문 생성, 주문항목 취소, 멱등 처리, 재고 차감을 바꾸면 `OrderServiceIntegrationTestSupport` 기반의 실제 MySQL 통합 테스트로 검증한다. Mock만으로는 락 순서, 유니크 제약, 트랜잭션 분리를 검증할 수 없다.

## 커밋·브랜치

- 커밋 메시지는 `유형: 한국어 요약` 형식이다. 유형은 `Feat`, `Fix`, `Refactor`, `Test`, `Chore`, `Docs` 중 하나다.
- 브랜치
  - 작업 브랜치는 `develop`에서 딴다. 이름은 `feature/…`, `refactor/…`, `test/…`, `chore/…`, `fix/…` 중 하나다.
  - 작업 브랜치는 `develop`에 병합한다.
  - 배포할 때는 `develop`을 `release`에 **squash merge**한다(release에 병합 커밋을 남기지 않기 위해).
  - 그다음 `release`를 `develop`에 다시 병합해 두 브랜치의 이력을 맞춘다. 이 역병합을 빠뜨리면 다음 squash merge에서 충돌이 난다.
- 배포는 `release`를 기준으로 GitHub Actions 워크플로를 수동으로 실행해서 한다. push로는 자동 배포되지 않는다.

## 계층과 모듈 경계

- 패키지는 도메인 단위(`com.ecommerce.api.<domain>`)이고, 안쪽은 `controller / service / repository / entity / dto / enums / vo / support / config`로 나눈다.
- 컨트롤러는 서비스만 호출한다. 저장소를 직접 쓰지 않는다. 응답은 `ResponseEntity`로 감싼다.
- 로그인한 사용자는 `@AuthenticationPrincipal CustomUserDetails`의 `getUserId()`와 `getUserRole()`로만 식별한다. 소유자 판단에 요청 본문이나 경로의 사용자 ID를 쓰면 안 된다. 경로나 쿼리로 받은 ID는 세션의 ID와 비교하는 데만 쓴다.
- **주문 생성 경로**
  - 컨트롤러는 `IdempotentOrderPlacementService`만 호출한다.
  - `OrderPlacementService.placeOrder`를 멱등 래퍼 밖에서 호출하지 않는다.
  - `IdempotencyService`의 메서드는 `MANDATORY` 전파라서, 바깥 트랜잭션 없이 부르면 예외가 난다.
- 멱등 처리를 새 기능에 붙일 때도 같은 모양으로 만든다. 기존 서비스를 감싸는 래퍼 서비스를 두고, 멱등 로직을 도메인 서비스 안에 섞지 않는다.

## 트랜잭션·동시성

- 조회 위주 서비스는 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에 `@Transactional`을 붙인다. 예외도 있다:
  - `MediaService`와 `ProductDeletionService`는 클래스 전체가 쓰기 트랜잭션이다.
  - `OrderPlacementService`는 메서드에만 트랜잭션이 있다.
  - `IdempotencyService`는 `MANDATORY`라서 호출하는 쪽의 트랜잭션에 참여한다.
  
  어떤 방식이든 쓰기가 `readOnly` 트랜잭션 안에서 실행되면 안 된다.
- `inventory`와 `product_stat`을 한 트랜잭션에서 함께 바꾸는 코드는 **반드시 `inventory` → `product_stat` 순서**로 락을 잡는다. 여러 상품을 다룰 때는 각 테이블 안에서 `product_id` 오름차순으로 처리한다. 순서를 어기면 주문 생성과 취소가 동시에 돌 때 데드락이 난다.
- 재고 차감은 반드시 조건부 UPDATE(`quantity >= ?`)로 한다. 먼저 조회한 뒤 애플리케이션에서 판단하고 저장하는 방식은 쓰지 않는다.
- JDBC 배치 UPDATE는 결과 배열의 모든 값이 `1`인지 확인하고, 아니면 `AppException`을 던져 롤백시킨다.
- 주문항목(`OrderItem`)의 상태는 엔티티 메서드로만 바꾼다. `@Version` 컬럼을 우회하는 bulk UPDATE로 상태를 바꾸지 않는다.
- 애플리케이션은 여러 인스턴스로 뜬다.
  - 요청 사이에 공유해야 하는 상태를 JVM 메모리(static 필드, 로컬 캐시)에 두지 않는다.
  - `@Scheduled` 작업은 모든 인스턴스에서 동시에 실행돼도 결과가 같아야 한다. 원자적 조건부 연산, `SPOP` 분배, 유니크 제약 같은 방법을 쓴다.

## 엔티티·스키마

- 엔티티는 `protected` 기본 생성자, 명시적 생성자나 정적 팩토리, 의미 있는 상태 변경 메서드로 만든다. 범용 setter를 새로 추가하지 않는다.
- enum 컬럼은 `EnumType.STRING`, ID는 `IDENTITY`, 시각은 `Instant`로 한다. 생성·수정 시각이 필요하면 `BaseTimeEntity`를 상속한다.
- 숨김 삭제 대상(`User`, `Product`)
  - `@SQLDelete`가 걸려 있어서 `repository.delete()`를 호출하면 `deleted = true`로 바뀐다.
  - 조회 필터(`@SQLRestriction`)는 없다. 그래서 `findById`는 삭제된 행도 돌려준다.
  - 사용자에게 보이는 조회와 쓰기 대상 조회는 `…AndDeletedFalse` 메서드나 `deleted = false` 조건을 써야 한다.
- 운영 설정은 `ddl-auto: validate`다.
  - 엔티티 매핑(테이블, 컬럼, 타입)을 바꾸면, 배포 전에 모든 대상 DB에 DDL을 직접 적용해야 한다. 적용하지 않으면 서버가 기동하지 않는다.
  - 엔티티 어노테이션에 없는 DB 객체(FULLTEXT 인덱스 등)도 직접 관리한다.
- 테스트는 `create-drop`으로 스키마를 엔티티에서 만든다. 그래서 운영 DB에만 있는 객체(FULLTEXT 인덱스)는 테스트 DB에 없다.

## 입출력 계약 패턴

- 요청 DTO는 `record`로 만들고, Bean Validation 메시지는 사용자에게 그대로 보여줄 한국어 문장으로 쓴다.
  - 기본값(페이지 0, 크기 20 등)은 record의 compact constructor에서 채운다.
  - **새로 만드는 필드 사이 조건(둘 중 하나 필수, 허용값 목록 등)은 compact constructor에 두지 않는다. 서비스나 Bean Validation(`@AssertTrue` 등)으로 검사한다.** compact constructor에서 던진 `AppException`은 감싸져서 전용 코드로 응답되지 않는다. `@RequestBody`는 `9001 "요청 Body 읽기 실패"`, `@ModelAttribute`는 500이 된다. 기존 코드의 이런 검사는 전역 예외 처리를 고칠 때 함께 옮긴다.
- 업무 오류는 모두 `AppException(ErrorCode[, 메시지])`로 던진다. 전역 핸들러가 `ErrorCode`의 HTTP 상태와 `{code, message}` 본문으로 바꾼다.
  - `IllegalStateException` 같은 일반 예외는 "일어나면 안 되는 상태"에만 쓴다. 이런 예외는 500(`9999`)이 된다.
- **새 오류 코드**
  - `ErrorCode` enum에 추가하고, 도메인 대역을 지킨다: `0xxx` 인증, `1xxx` 사용자, `2xxx` 상품(`25xx` 재고), `3xxx` 주문, `6xxx` 리뷰, `7xxx` 장바구니, `75xx` 쿠폰, `8xxx` 미디어, `9xxx` 공통(`91xx` 멱등), `9999` 미식별.
  - 코드는 4자리 **문자열**이다. 한 번 쓴 코드 번호는 의미를 바꾸거나 다른 오류에 재사용하지 않는다.
  - 404로 숨겨야 하는 권한 오류는 대응하는 "없음" 오류의 HTTP 상태와 메시지를 그대로 가져다 쓴다(`ORDER_ACCESS_DENIED`, `REVIEW_WRITER_MISMATCH` 방식).
- 페이지를 나누는 목록 응답은 `items`(또는 도메인별 목록 필드), `page`, `size`, `totalCount`, `totalPages`, `hasNext` 필드를 가진다. 상품 목록과 주문항목 목록의 페이지 크기는 20, 50, 100만 허용한다.

## 환경 설정

- 공통이고 비밀이 아닌 값만 `application.yaml`에 둔다. 환경마다 다른 값과 비밀값은 프로필 파일에서 `${ENV_NAME}` 자리표시자로 받는다.
- `application-secret.yaml`, `compose.yaml`, `.env`, GCP 키 파일(`api/infra/`)은 커밋하지 않는다. 이미 `.gitignore`에 있고, 이 항목을 지우지 않는다.
- 프로필: `local`(로컬, `application-secret.yaml` import), `dev`(클라우드 실행), `loadtest`(부하테스트, 인증 우회 가능). **프로필 없이는 기동하지 않는다.** DB 주소 등 필수 자리표시자가 비어 있기 때문이다.
- 설정 묶음은 `@ConfigurationProperties` record로 받는다. 범위 검사가 필요하면 `@Validated`를 붙인다.
