# 미해결 문제: 운영·빌드·k6

## 로컬 빌드 산출물에 비밀 설정 파일이 포함된다
- **조건**: 로컬에서 `./gradlew bootJar`나 `docker build`를 실행한다.
- **증상**: `src/main/resources/application-secret.yaml`(DB·Redis 비밀번호, GCP 설정)이 jar와 이미지 안에 들어간다. `api/.dockerignore`가 없다.
- **영향**: 로컬 이미지를 레지스트리에 올리거나 jar를 공유하면 자격 증명이 유출된다.
- **지금 해결하지 않는 이유**: 비밀 파일 위치를 옮길지(리소스 밖 외부 경로로 import), `.dockerignore`와 `bootJar` 제외 설정을 둘지 결정해야 한다.

## 스케줄 작업 두 개가 스케줄러 스레드 하나를 함께 쓴다
- **증상**: 쿠폰 캐시 적재(`CouponEventCacheScheduler`)와 조회수 반영(`ProductViewCountService`)은 둘 다 `@Scheduled(fixedDelay = 30_000)`이다(이전 실행이 끝나고 30초 뒤 다시 실행). 스케줄러 스레드 수 설정(`spring.task.scheduling.pool.size`)과 가상 스레드 설정이 없어서, Spring Boot 기본값인 스레드 1개를 두 작업이 함께 쓴다(기본값 근거 추정, 실행 확인 안 함).
- **영향**: 한 작업이 오래 걸리면 다른 작업이 밀린다. 예를 들어 조회수 반영이 Redis·DB 지연으로 쿠폰 이벤트 시작 시각을 넘겨 스레드를 붙잡으면, 이벤트가 캐시에 올라가지 않아 발급이 `7503`으로 거절된다. 조회수 반영은 한 번에 처리할 양에 상한이 있어 평소에는 드물다.
- **고칠 방향 후보**: 스케줄러 스레드를 2개로 늘린다(설정 한 줄). 또는 두 작업을 서로 다른 실행기로 나눈다.
- **지금 해결하지 않는 이유**: 사용자가 나중에 진행한다(2026-09-30 동기화 작업). `status.md` 남은 작업 "Redis 장애 대응 보강"과 함께 볼 수 있다.

## 부하테스트 데이터는 저장소만으로 재현할 수 없다
- **증상**: k6 스크립트가 읽는 데이터 파일(`k6/data/`)과 그 파일을 만드는 SQL은 로컬에만 있다. 새 환경에서는 저장소만 보고 데이터를 준비할 수 없다. 빠진 것:
  - 입력 목록을 DB에서 뽑는 SQL: `available_buyer_email.json`, `available_seller_email.json`, `reviewable_buyer_email.json`, `product_name_keywords.json` 등
  - 카테고리 시드: 상품 등록 스크립트는 카테고리 ID 10~1009가 있다고 가정한다. `data/mock_product_category.json`은 어떤 스크립트도 읽지 않고, 이름 260개가 관리자 API의 15자 제한을 넘는다.
  - 재고 동시성 후보 CSV(`order_stock_concurrency_candidates.csv`)와 그것을 만드는 SQL
  - mock 가입 데이터(`mock_nickname_email.json`)에 30자를 넘는 이메일이 21개 있어 그 행의 가입이 실패한다. 상품 등록 스크립트가 판매자로 쓰는 앞 71행과 DB에서 뽑은 판매자 목록(`available_seller_email.json`, 80명)도 서로 맞지 않는다.
- **영향**: 부하테스트 결과를 다른 환경에서 다시 만들 수 없다. 주문·쿠폰 혼합 후보는 저장소의 SQL(`infra/sql/order_coupon_mixed_candidates_V3.sql`)로 만들 수 있지만, 그 SQL도 사용자·상품·재고 데이터가 이미 있다고 가정한다.
- **지금 해결하지 않는 이유**: 범위 밖으로 정했다(2026-09-30 동기화 작업). 스크립트는 지금 서버에 맞게 고쳤고, 데이터 준비 순서는 `k6/CLAUDE.md`에 적었다.
