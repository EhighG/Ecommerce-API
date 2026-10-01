# 운영

현재는 로컬에서만 실행한다. 클라우드(GCP) 환경은 비용 문제로 내렸지만, 아래 배포 절차로 같은 구성을 다시 올릴 수 있다. 배포 워크플로는 수동으로만 실행된다.

## 사전 준비

| 준비물 | 용도 |
|---|---|
| JDK 21 | 빌드·실행. Gradle Wrapper가 toolchain으로 확인한다 |
| Docker | 로컬 MySQL·Redis 실행, 통합 테스트(Testcontainers) |
| GCP 서비스 계정 키 또는 `gcloud` 로그인 | 서버 기동에 필요하다(이미지 기능을 쓰지 않아도. `engineering-notes.md`의 GCP 자격 증명 함정) |
| k6 | 부하테스트를 할 때만 |

## 로컬 첫 실행 (순서대로)

1. **MySQL과 Redis 띄우기**
   - `api/compose.yaml`은 커밋되지 않는 파일이다. 없으면 아래 조건으로 만든다:
     - MySQL 8.4: 호스트 포트 `3307`, DB `ecommerce`, `--character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci`. 검색에 필요한 서버 옵션은 `api/db/schema.sql` 머리에 있다
     - Redis 7: 호스트 포트 `6379`, `--requirepass <비밀번호>`
   - 실행:
     ```bash
     cd api
     docker compose up -d mysql redis
     ```
2. **스키마 만들기**
   - 애플리케이션은 테이블을 만들지 않는다(`validate`). 1단계 DB가 떠 있어야 한다.
   - 빈 DB에 `api/db/schema.sql`을 실행한다. 테이블과 FULLTEXT 인덱스를 모두 만든다. 서버 옵션과 인덱스를 확인하는 쿼리는 파일 머리에 있다.
     ```bash
     mysql -h 127.0.0.1 -P 3307 -u <사용자> -p ecommerce < api/db/schema.sql
     ```
3. **기초 데이터 넣기**
   - 카테고리: 관리자 API로 만들거나, 로컬 전용 SQL을 쓴다.
   - 관리자 계정: 가입 API로는 만들 수 없으므로 SQL로 직접 넣는다. `password`에는 BCrypt 해시를 넣는다. NOT NULL 컬럼은 모두 채워야 한다.
     ```sql
     INSERT INTO users (email, nickname, password, role, deleted, created_at, updated_at)
     VALUES ('<이메일, 30자 이하>', '<닉네임, 20자 이하>', '<BCrypt 해시>', 'ADMIN', false, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
     ```
4. **비밀 설정 파일 만들기**: `api/src/main/resources/application-secret.yaml`(커밋 금지)에 아래 "환경 설정" 표의 키를 `KEY: value` 형식으로 적는다. `local` 프로필이 이 파일을 import한다.
5. **GCP 자격 증명**: 실행 환경의 **OS 환경변수**로 `GOOGLE_APPLICATION_CREDENTIALS=<키 파일 절대경로>`를 설정한다. 또는 `gcloud auth application-default login`을 실행한다. 4단계 파일에 적어도 소용이 없다(`engineering-notes.md`).
6. **실행**
   ```bash
   cd api
   SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
   ```
   - 확인: `curl localhost:8090/actuator/health` 응답의 `status`가 `UP`이다.
   - API 기본 경로는 `http://localhost:8081/api`다.

## 환경 설정

| 변수 | 의미 | 쓰는 프로필 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` / `dev` / `loadtest` 중 하나(`standards.md`의 "환경 설정") | 전체 |
| `RDB_HOST`, `RDB_PORT`, `RDB_NAME`, `RDB_USERNAME`, `RDB_PASSWORD` | MySQL 접속 정보. URL에 `rewriteBatchedStatements=true`가 붙는다 | 전체 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 접속 정보(세션, 쿠폰, 조회수) | 전체 |
| `GCP_PROJECT_ID`, `GCS_BUCKET` | GCS 프로젝트와 버킷. 공개 URL은 `https://storage.googleapis.com/{버킷}/{경로}` | 전체 |
| `GCS_UPLOAD_PREFIX` | 업로드 객체 경로 접두어(예: `product_images`). 빈 문자열이면 날짜 폴더부터 시작한다 | 전체 |
| `GOOGLE_APPLICATION_CREDENTIALS` | 서비스 계정 키 파일 경로(**OS 환경변수**). VM 서비스 계정을 쓰면 생략한다. 이때 서명 URL은 IAM `signBlob` API로 만들어지므로, 그 서비스 계정에 자기 자신에 대한 토큰 생성자 권한(`roles/iam.serviceAccountTokenCreator`)이 있고 IAM Service Account Credentials API가 켜져 있어야 한다(라이브러리 동작 기준, 확인 필요) | 전체 |
| `HIKARI_MAX_POOL_SIZE`, `HIKARI_MIN_IDLE`, `HIKARI_CONN_TIMEOUT_MS` | 커넥션 풀. 기본값은 10 / 10 / 30000ms | 전체 |
| `PRODUCT_VIEW_COUNT_FLUSH_BATCH_SIZE` | 조회수 반영 1회에 처리할 상품 수(1 이상, 기본값 100) | 전체 |
| `PRODUCT_VIEW_COUNT_FLUSH_MAX_BATCHES_PER_RUN` | 30초 주기 1회당 최대 청크 수(1 이상, 기본값 10) | 전체 |
| `LOADTEST_AUTH_ENABLED`, `LOADTEST_AUTH_SECRET` | 헤더 기반 인증 우회. 조건과 주의는 `security.md`의 "부하테스트용 인증 우회" | `loadtest` |
| `HIBERNATE_SLOW_QUERY_MS` | 느린 쿼리 로그 기준(기본 1000) | `loadtest` |

- 필수 변수를 빠뜨리면 빈 값이 아니라 `${…}` 문자열이 들어간다(`engineering-notes.md`).
- HikariCP 지표의 풀 이름 태그(`spring.datasource.name`)는 `local`과 `dev`가 `main`, `loadtest`가 `loadtest`다. 대시보드 쿼리는 프로필에 맞는 이름으로 거른다.
- 포트는 서비스 `8081`(context path `/api`), 관리 `8090`(`/actuator/health`, `/actuator/prometheus`)이다.

## 로컬 수평 확장 (선택)

`api/infra/scaleout-local/`에 같은 이미지 인스턴스 2대(`api-1`, `api-2`), nginx(호스트 `8080`에서 두 인스턴스로 분배), Prometheus(`9090`), MySQL(`3307`), Redis(`6379`)를 띄우는 예시가 있다. 스케줄 작업이나 세션을 여러 인스턴스로 확인할 때 쓴다.

1. `.env.example`을 같은 폴더의 `.env`로 복사해 값을 채운다. `.env`는 커밋하지 않는다.
2. 이 폴더에서 `docker compose -f compose.scaleout-local.yaml up -d mysql redis`로 DB를 먼저 띄우고, 빈 DB에 `api/db/schema.sql`을 실행한다.
3. `docker compose -f compose.scaleout-local.yaml up -d --build`로 나머지를 띄운다.
- 인스턴스는 `local` 프로필로 뜨므로 부하테스트 인증 우회는 꺼져 있다. `local` 프로필은 `application-secret.yaml`을 import하므로 이 파일이 있어야 이미지가 기동한다. 이 이미지는 로컬에서만 쓴다.
- 기본 로컬 구성(`api/compose.yaml`)과 같은 호스트 포트를 쓰므로 둘을 동시에 띄우지 않는다.

## 배포 (GCP, 다시 올릴 때)

전제: Artifact Registry 저장소(`asia-northeast3`), 인스턴스 템플릿과 Managed Instance Group(인스턴스마다 이미지 `:latest`를 실행하고 위 환경변수를 주입), MySQL, Redis, GCS 버킷(공개 읽기), GitHub 저장소 변수가 준비돼 있어야 한다.

- GitHub 저장소 변수: `GCP_PROJECT_ID`, `GCE_ZONE`, `ARTIFACT_REPOSITORY`, `API_SERVER_IMAGE_NAME`, `MIG_NAME`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_GITHUB_ACTIONS_SERVICE_ACCOUNT`

순서:
1. DB 스키마 변경이 있으면 **먼저** 클라우드 MySQL에 DDL을 적용한다. 새 이미지는 `validate`에 실패하면 기동하지 않는다.
2. `release`를 배포할 `develop` 커밋으로 옮긴다. fast-forward만 한다: `git push origin develop:release`
3. GitHub Actions에서 `Backend Deploy` 워크플로를 `release` 기준으로 수동 실행한다. 워크플로가 하는 일:
   1. 테스트
   2. 이미지 빌드
   3. `:{커밋 SHA}`와 `:latest`로 push
   4. MIG 인스턴스 전체 교체
   5. 안정 상태가 될 때까지 최대 20분 대기
4. 배포가 끝나면 그 커밋에 태그를 붙인다: `git tag deploy-YYYY-MM-DD <커밋> && git push origin deploy-YYYY-MM-DD`
- 교체는 모든 인스턴스를 동시에 내리고 새로 띄운다. 그래서 배포 중에는 서비스가 끊긴다.
- 워크플로의 안정 대기 단계는 MIG 이름이 `instance-group-2`로 고정되어 있다. `MIG_NAME` 변수와 이름이 다르면 대기 단계가 실패한다.

## 부하테스트 (`k6/`, `infra/`)

- 대상 서버는 `loadtest` 프로필로 띄우고 `LOADTEST_AUTH_ENABLED=true`와 추측할 수 없는 `LOADTEST_AUTH_SECRET`을 준다.
- **자동 실행**: 부하 발생기 VM에서 `infra/scripts/order-coupon-mixed-loadtest-auto.sh`를 실행한다.
  - 미리 준비할 것. 스크립트는 만들지 않고, 없으면 종료한다.
    - `/opt/ecommerce/loadtest/.env`: `RDB_HOST`, `RDB_PORT`(기본 3306), `RDB_NAME`, `RDB_USER`, `RDB_PASSWORD`, `BASE_URL`, `LOADTEST_AUTH_SECRET`. DB 사용자 키 이름이 서버의 `RDB_USERNAME`과 다르다.
    - `/opt/ecommerce/repo/Ecommerce-project`: 저장소 clone. 스크립트는 pull하지 않는다.
    - 대상 DB의 `loadtest_marker` 표. 부하테스트 전용 DB에만 한 번 만든다: `CREATE TABLE loadtest_marker (id int primary key);`. 이 표가 없는 DB에는 데이터를 넣지 않고 멈춘다.
    - `sudo`, `mysql` 클라이언트, `k6`
  - 하는 일
    1. DB 접속과 `loadtest_marker` 표를 확인한다. DB 비밀번호는 권한 600 임시 옵션 파일로 넘겨 프로세스 목록에 보이지 않게 한다.
    2. `infra/sql/order_coupon_mixed_candidates_V3.sql`을 실행한다. 장바구니 항목을 넣고, 실행마다 새 쿠폰 이벤트와 발급 쿠폰을 만든 뒤, 주문 후보를 `k6/data/order_coupon_mixed_candidates.csv`로 받는다(데이터 준비 + 후보 추출). 기존 쿠폰 데이터는 지우지 않는다.
    3. `REST_AFTER_DATA_PREPARE_SECONDS`(기본 30초) 쉰 뒤 k6 주문·쿠폰 혼합 시나리오를 실행한다.
    4. 결과를 `/opt/ecommerce/results/perf/{RUN_ID}`에 저장한다.
  - 주요 입력(환경변수): `TEST_MODE`(`fixed` 또는 `capacity`), `ORDER_RATE`(기본 60. k6 스크립트를 직접 돌릴 때의 기본값은 30이다), `DURATION`, `WARMUP_*`, `CANCEL_RATIO`(기본 0.3), p99 임계값(`ORDER_CREATE_P99_MS` 등), `RUN_ID`
- **스크립트를 직접 실행할 때**: 키 이름은 `k6/.env.example`에, 스크립트별 요구 조건과 데이터 준비 순서는 `k6/CLAUDE.md`에 있다.
