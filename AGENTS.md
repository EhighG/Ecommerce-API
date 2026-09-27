# Ecommerce-API

오픈마켓형 이커머스의 REST API 서버다. 단일 Spring Boot 4(Java 21) 애플리케이션이고, MySQL, Redis, GCS를 쓴다. 약 7천 줄 규모의 1인 포트폴리오 프로젝트로, 프론트엔드는 없다.

목표는 기능의 가짓수가 아니다. 주문·재고·쿠폰의 **정합성**과, 부하테스트로 확인한 **성능·확장성** 개선이 핵심이다. 새 기술(메시지 큐, MSA, 새 저장소)을 들이거나 추상화를 늘리기 전에, 그 변경이 정합성이나 측정된 병목 해결에 필요한지 먼저 따진다.

## 프로젝트 구조

```
Ecommerce-project/
├── CLAUDE.md / AGENTS.md          → 이 문서(두 파일 내용 동일)
├── docs/
│   ├── architecture.md            → 구성 요소 연결, 주문 생성 흐름, 모듈 지도, 스케줄 작업
│   ├── business-rules.md          → 업무 규칙: 주문 상태, 금액·할인 계산, 쿠폰, 재고, 삭제 전파
│   ├── security.md                → 인증 흐름, 역할·소유자 인가 표, 404/403 기준, 자격 증명
│   ├── standards.md               → 반드시 지킬 규칙: 빌드, 검증, 브랜치, 락 순서, 스키마, 오류 코드
│   ├── engineering-notes.md       → 함정(증상→원인→해결), 동작 방식, 반복 작업 체크리스트
│   ├── operations.md              → 로컬 설정, 명령, 환경변수, 배포, 부하테스트 실행
│   ├── contracts.md               → 외부 REST API 계약(입력·출력·오류 코드)
│   ├── api-spec.md, project-requirements.md, assets/  → 기존 문서(낡음, 개편 예정. 기준으로 쓰지 않는다)
│   └── tracking/
│       ├── status.md              → 완료·남은 작업(우선순위)
│       ├── decisions/             → 결정 기록(index.md + 0001~0005)
│       └── findings.md            → 미해결 문제
├── api/
│   ├── AGENTS.md                  → 애플리케이션 전체: 빌드·테스트·공통 구현 방식
│   └── src/main/java/com/ecommerce/api/
│       ├── auth/AGENTS.md         → 로그인 필터, 보안 설정, 부하테스트 인증 우회
│       ├── user/AGENTS.md         → 가입, 탈퇴(판매자 상품 일괄 삭제)
│       ├── product/AGENTS.md      → 상품, 카테고리, 이미지 연결, 통계, 조회수
│       ├── inventory/AGENTS.md    → 재고 차감·복구·수정
│       ├── media/AGENTS.md        → GCS 서명 URL, 업로드 등록
│       ├── cartitem/AGENTS.md     → 장바구니
│       ├── order/AGENTS.md        → 주문 생성(멱등), 주문항목 상태 전이·취소
│       ├── coupon/AGENTS.md       → 쿠폰 이벤트, Redis 선착순 발급, 사용·복구
│       ├── review/AGENTS.md       → 리뷰, 평점 통계
│       ├── idempotency/AGENTS.md  → 멱등 키 기록
│       └── common/AGENTS.md       → 오류 코드, 전역 예외 처리, 공통 설정
├── k6/AGENTS.md                   → 부하테스트 스크립트
└── infra/                         → 부하테스트 자동화 스크립트, 후보 데이터 SQL
```

## 절대 규칙

1. **`inventory` → `product_stat` 락 순서와 `product_id` 오름차순을 지킨다.** 재고 차감은 조건부 UPDATE로만 하고, 재고는 음수가 되면 안 된다. 어기면 데드락이나 초과 판매가 난다.
2. **주문 생성은 멱등 래퍼(`IdempotentOrderPlacementService`)를 통해서만 한다.** 주문 생성과 취소는 전부 성공하거나 전부 롤백되어야 한다. 재고, 쿠폰, 장바구니, 통계 중 하나만 반영되면 안 된다.
3. **소유자 검사는 서비스에서 세션의 사용자 ID로 한다.** 사용자 소유 자원(주문, 주문항목, 리뷰)의 남의 접근은 404, 공개 자원(상품, 재고)의 남의 수정은 403이다. URL 역할 규칙만으로는 충분하지 않다.
4. **엔티티 매핑을 바꾸면 DDL을 직접 작성해 모든 DB에 적용한다**(`ddl-auto: validate`, 마이그레이션 도구 없음). 테스트는 스키마 누락을 잡지 못한다.
5. **여러 인스턴스가 동시에 돈다.** JVM 메모리에 공유 상태를 두지 않는다. `@Scheduled` 작업은 동시에 실행해도 결과가 같아야 한다.

전체 규칙은 `docs/standards.md`에 있다.

## 작업 전에 읽을 것

- 항상: `docs/standards.md`, `docs/engineering-notes.md`, 작업할 모듈의 `AGENTS.md`
- 주문 생성·취소, 재고, 쿠폰 사용을 바꿀 때: `docs/business-rules.md`의 "주문 생성", "주문항목 상태", "쿠폰"과 `docs/architecture.md`의 대표 흐름. 그리고 `docs/standards.md`의 트랜잭션·동시성 규칙
- 인증, 인가, 새 엔드포인트: `docs/security.md`의 인가 표와 404/403 기준. 그리고 `docs/engineering-notes.md`의 "API 엔드포인트를 추가할 때"
- 엔티티나 테이블 변경: `docs/engineering-notes.md`의 "엔티티나 테이블을 바꿀 때"와 FULLTEXT 인덱스 함정
- 응답 형식이나 오류 코드 변경: `docs/contracts.md`의 공통 규칙, `docs/standards.md`의 오류 코드 대역, `k6/AGENTS.md`(스크립트가 응답을 파싱함)
- 스케줄 작업, Redis 키 변경: `docs/architecture.md`의 스케줄 작업, `docs/tracking/decisions/0004-horizontal-scale-out.md`
- 쿠폰 만료나 상태 판정: `docs/tracking/decisions/0005-coupon-expiry-by-timestamp.md`
- `docs/api-spec.md`와 `docs/project-requirements.md`는 현재 코드와 어긋난다. 규칙이나 계약의 근거로 쓰지 않는다.

## 문제를 발견했을 때

**즉시 사용자에게 알릴 것:**
- 재고가 음수가 되거나 초과 판매가 나는 경로
- 쿠폰 중복 사용, 이벤트당 1인 1장이나 수량 한도를 넘는 발급
- 주문 부분 반영(재고는 줄었는데 주문이 없는 경우 등)
- 같은 멱등 키로 주문이 두 건 생기는 경우
- 남의 주문·리뷰·장바구니를 조회하거나 변경할 수 있는 인가 우회
- 부하테스트 인증 우회가 `loadtest` 외의 환경에서 켜지거나, 추측 가능한 비밀값으로 켜질 수 있는 경로
- 자격 증명이 커밋되거나, 빌드 산출물(jar, 이미지)에 담겨 외부로 나갈 수 있는 경우
- DB 데드락이 재발한 경우

**그 밖의 문제:** 그 자리에서 해결하지 못하면 `docs/tracking/findings.md`에 기록한다. 조건, 증상, 영향, 지금 해결하지 않는 이유를 함께 적는다.
