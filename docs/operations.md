# 운영

현재는 로컬에서만 실행한다. 클라우드(GCP) 환경은 비용 문제로 내렸지만, 아래 배포 절차로 같은 구성을 다시 올릴 수 있다. 배포 워크플로는 수동으로만 실행된다.

## 사전 준비

| 준비물 | 용도 |
|---|---|
| JDK 21 | 빌드·실행. Gradle Wrapper가 toolchain으로 확인한다 |
| Docker | 로컬 MySQL·Redis 실행, 통합 테스트(Testcontainers) |
| GCP 서비스 계정 키 또는 `gcloud` 로그인 | 서버 기동에 필요하다. 이미지 기능을 쓰지 않아도 GCS 클라이언트를 기동 시점에 만든다 |
| k6 | 부하테스트를 할 때만 |

## 로컬 첫 실행 (순서대로)

1. **MySQL과 Redis 띄우기**
   - `api/compose.yaml`은 커밋되지 않는 파일이다. 없으면 아래 조건으로 만든다:
     - MySQL 8.4: 호스트 포트 `3307`, DB `ecommerce`, `--character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --ngram_token_size=2 --innodb_ft_enable_stopword=OFF`
     - Redis 7: 호스트 포트 `6379`, `--requirepass <비밀번호>`
   - 실행:
     ```bash
     cd api
     docker compose up -d mysql redis
     ```
   - ngram 옵션은 서버를 띄울 때만 정할 수 있다. 나중에 바꾸려면 FULLTEXT 인덱스를 다시 만들어야 한다.
2. **스키마 만들기**
   - 애플리케이션은 테이블을 만들지 않는다(`validate`). 1단계 DB가 떠 있어야 한다.
   - 저장소에는 전체 DDL 스크립트가 없다. 로컬에 보관 중인 덤프(`ignore/` 아래, 커밋되지 않음)를 복원한다.
   - 덤프가 없으면 엔티티에서 만드는 수밖에 없다. 로컬에서만 `ddl-auto`를 잠시 `create`로 바꿔 한 번 띄운 뒤 `validate`로 되돌린다. 이 변경은 커밋하지 않는다.
   - 그다음 FULLTEXT 인덱스를 만든다:
     ```sql
     ALTER TABLE product ADD FULLTEXT INDEX ft_product_name_description (name, description) WITH PARSER ngram;
     ```
3. **기초 데이터 넣기**
   - 카테고리: 관리자 API로 만들거나, 로컬 전용 SQL을 쓴다.
   - 관리자 계정: 가입 API로는 만들 수 없으므로 SQL로 직접 넣는다. `users.password`에는 BCrypt 해시를 넣고, `role`은 `'ADMIN'`, `deleted`는 `false`로 한다.
4. **비밀 설정 파일 만들기**: `api/src/main/resources/application-secret.yaml`(커밋 금지)에 아래 "환경 설정" 표의 키를 `KEY: value` 형식으로 적는다. `local` 프로필이 이 파일을 import한다.
5. **GCP 자격 증명**: 실행 환경의 **OS 환경변수**로 `GOOGLE_APPLICATION_CREDENTIALS=<키 파일 절대경로>`를 설정한다. 또는 `gcloud auth application-default login`을 실행한다. 4단계 파일에 적은 값은 Google 라이브러리가 읽지 않는다.
6. **실행**
   ```bash
   cd api
   SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
   ```
   - 확인: `curl localhost:8090/actuator/health` 응답이 `{"status":"UP"}`이다.
   - API 기본 경로는 `http://localhost:8081/api`다.

## 개발·검증 명령

| 명령 (`api/`에서) | 설명 |
|---|---|
| `./gradlew test` | 전체 테스트. Docker가 필요하다(MySQL 8.4 컨테이너가 자동으로 뜬다). `reproduction` 태그는 빠진다 |
| `./gradlew test --tests '*CartItemServiceTest'` | 특정 테스트만 |
| `./gradlew bootJar` | 실행 jar 생성(`build/libs/`) |
| `./gradlew compileJava` | Querydsl Q클래스 생성 포함 컴파일 |
| `docker build -t ecommerce-api .` | 배포 이미지 빌드. 내부에서 Gradle 8.14.3으로 `bootJar`를 실행한다. **로컬에서 빌드하면 `application-secret.yaml`이 이미지 안에 들어간다**(`src`를 통째로 복사함). 로컬 이미지는 레지스트리에 push하지 않는다 |

## 환경 설정

| 변수 | 의미 | 쓰는 프로필 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` / `dev` / `loadtest` 중 하나. 없으면 기동하지 않는다 | 전체 |
| `RDB_HOST`, `RDB_PORT`, `RDB_NAME`, `RDB_USERNAME`, `RDB_PASSWORD` | MySQL 접속 정보. URL에 `rewriteBatchedStatements=true`가 붙는다 | 전체 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 접속 정보(세션, 쿠폰, 조회수) | 전체 |
| `GCP_PROJECT_ID`, `GCS_BUCKET` | GCS 프로젝트와 버킷. 공개 URL은 `https://storage.googleapis.com/{버킷}/{경로}` | 전체 |
| `GCS_UPLOAD_PREFIX` | 업로드 객체 경로 접두어(예: `product_images`). 빈 문자열이면 날짜 폴더부터 시작한다. 변수를 아예 설정하지 않으면 문자 그대로의 `${GCS_UPLOAD_PREFIX}`가 접두어가 된다 | 전체 |
| `GOOGLE_APPLICATION_CREDENTIALS` | 서비스 계정 키 파일 경로(**OS 환경변수**). VM 서비스 계정을 쓰면 생략한다 | 전체 |
| `HIKARI_MAX_POOL_SIZE`, `HIKARI_MIN_IDLE`, `HIKARI_CONN_TIMEOUT_MS` | 커넥션 풀. 기본값은 10 / 10 / 30000ms | 전체 |
| `PRODUCT_VIEW_COUNT_FLUSH_BATCH_SIZE` | 조회수 반영 1회에 처리할 상품 수(1 이상, 기본값 없음) | 전체 |
| `PRODUCT_VIEW_COUNT_FLUSH_MAX_BATCHES_PER_RUN` | 30초 주기 1회당 최대 청크 수(1 이상, 기본값 없음) | 전체 |
| `LOADTEST_AUTH_ENABLED`, `LOADTEST_AUTH_SECRET` | 헤더 기반 인증 우회. 켤 때는 비밀값을 반드시 명시적으로 설정한다. 설정하지 않으면 문자 그대로의 `${LOADTEST_AUTH_SECRET}`가 비밀값이 되어 기동 검사를 통과한다. **실사용 환경 금지** | `loadtest` |
| `HIBERNATE_SLOW_QUERY_MS` | 느린 쿼리 로그 기준(기본 1000) | `loadtest` |

- 프로필별 차이
  - `local`: SQL·바인딩·트랜잭션 로그가 debug/trace이고, Hibernate 통계가 켜져 있다.
  - `dev`: 로그가 기본 수준이다.
  - `loadtest`: SQL 로그를 끄고, Tomcat MBean과 HTTP 요청 히스토그램 지표를 켠다. Hibernate 배치 설정은 없다.
- 포트는 서비스 `8081`(context path `/api`), 관리 `8090`(`/actuator/health`, `/actuator/prometheus`)이다.

## 배포 (GCP, 다시 올릴 때)

전제: Artifact Registry 저장소(`asia-northeast3`), 인스턴스 템플릿과 Managed Instance Group(인스턴스마다 이미지 `:latest`를 실행하고 위 환경변수를 주입), MySQL, Redis, GCS 버킷(공개 읽기), GitHub 저장소 변수가 준비돼 있어야 한다.

- GitHub 저장소 변수: `GCP_PROJECT_ID`, `GCP_REGION`, `GCE_INSTANCE_NAME`, `GCE_ZONE`, `ARTIFACT_REPOSITORY`, `API_SERVER_IMAGE_NAME`, `MIG_NAME`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_GITHUB_ACTIONS_SERVICE_ACCOUNT`

순서:
1. DB 스키마 변경이 있으면 **먼저** 클라우드 MySQL에 DDL을 적용한다. 새 이미지는 `validate`에 실패하면 기동하지 않는다.
2. `develop`을 `release`에 squash merge한 뒤, `release`를 `develop`에 역병합한다.
3. GitHub Actions에서 `Backend Deploy` 워크플로를 `release` 기준으로 수동 실행한다. 워크플로가 하는 일:
   1. 테스트
   2. 이미지 빌드
   3. `:{커밋 SHA}`와 `:latest`로 push
   4. MIG 인스턴스 전체 교체
   5. 안정 상태가 될 때까지 최대 20분 대기
- 교체는 모든 인스턴스를 동시에 내리고 새로 띄운다. 그래서 배포 중에는 서비스가 끊긴다.
- 워크플로의 안정 대기 단계는 MIG 이름이 `instance-group-2`로 고정되어 있다. `MIG_NAME` 변수와 이름이 다르면 대기 단계가 실패한다.

## 부하테스트 (`k6/`, `infra/`)

- 대상 서버는 `loadtest` 프로필로 띄운다. 부하 발생기 VM에서 `infra/scripts/order-coupon-mixed-loadtest-auto.sh`를 실행하면 다음을 차례로 한다:
  1. `/opt/ecommerce` 아래에 `.env`와 저장소를 둔다.
  2. 후보 데이터 SQL을 실행하고 CSV를 만든다.
  3. k6 주문·쿠폰 혼합 시나리오를 실행한다.
  4. 결과를 `results/perf/{RUN_ID}`에 저장한다.
- 주요 입력(환경변수)
  - `TEST_MODE`: `fixed` 또는 `capacity`
  - `ORDER_RATE`, `DURATION`
  - `WARMUP_*`
  - `CANCEL_RATIO`: 기본 0.3
  - p99 임계값: `ORDER_CREATE_P99_MS` 등
- 스크립트를 직접 실행하려면 `BASE_URL`(`http://호스트:8081/api`)과 `LOADTEST_AUTH_SECRET`이 필요하다. `k6/.env`나 환경변수로 준다.
- `k6/data/`는 커밋하지 않는다(개인정보성 테스트 계정, 대용량 CSV).
