# common — 오류 모델, 전역 예외 처리, 공통 설정

## 담당 범위
- 담당하는 것:
  - `ErrorCode`(HTTP 상태 + 4자리 문자열 코드 + 기본 메시지), `AppException`, `ApiError`(`{code, message}`), `GlobalControllerAdvice`
  - `BaseTimeEntity`(`createdAt`, `updatedAt`, JPA Auditing), `Sha256Hasher`
  - 설정: GCS 클라이언트와 자격 증명, `JPAQueryFactory`, CORS, `StorageProperties`
- 담당하지 않는 것:
  - 도메인 규칙
  - 보안 필터 단계의 오류 응답. auth가 처리한다. 401은 본문이 없고, 403은 Spring Boot 기본 오류 JSON(`code` 없음)이다.
- `ApiResponse`는 전체가 주석 처리된 파일이다. 응답을 감싸는 래퍼는 쓰지 않는다. 성공 응답은 DTO나 ID를 그대로 보낸다.

## 항상 지켜야 할 것
- 새 오류는 `ErrorCode`에 추가한다.
  - 대역: `0xxx` 인증, `1xxx` 사용자, `2xxx` 상품(`25xx` 재고), `3xxx` 주문, `6xxx` 리뷰, `7xxx` 장바구니, `75xx` 쿠폰, `8xxx` 미디어, `9xxx` 공통(`91xx` 멱등), `9999` 미식별.
  - 코드는 **문자열**이다. 이미 쓴 번호는 재사용하지 않는다.
  - 기본 메시지는 사용자에게 보여줄 한국어 문장이다.
- 존재를 숨기는 권한 오류는 대응하는 "없음" 오류의 상태와 메시지를 참조해서 정의한다. 예: `ORDER_ACCESS_DENIED(ORDER_NOT_FOUND.httpStatus, "3001", ORDER_NOT_FOUND.message)`.
- 전역 핸들러가 매핑하는 것:
  - `AppException` → 해당 코드
  - Bean Validation(본문, 쿼리) → `9001` + 위반 필드 메시지. 쿼리 record 바인딩의 타입 변환 실패도 이 경로라 프레임워크 영문 메시지가 나간다
  - 경로 변수 타입 불일치, 본문 읽기 실패 → `9001` + 고정 문구
  - `OptimisticLockingFailureException` → `3004`(409)
  - 그 밖의 예외 → `9999`(500, 스택 로그)
  
  `OptimisticLockingFailureException`은 무조건 주문 상태 충돌로 매핑된다. 다른 엔티티에 버전 락을 추가하면 분기가 필요하다.
- 현재 결함이다. 요청 record의 생성자에서 던진 `AppException`이 감싸져서 `@RequestBody`는 `9001 "요청 Body 읽기 실패"`, `@ModelAttribute`는 500이 된다. 고칠 때는 생성자 검사를 record의 `@AssertTrue`로 옮긴다. 핸들러에서 원인 예외를 풀어 주는 방식은 쓰지 않는다(`docs/standards.md`). 쿼리 파라미터 타입 오류(enum, 숫자)도 프레임워크 영문 메시지 대신 한국어 문구로 바꾸고, 잘못된 `sortBy`에는 `2003`을 쓴다.

## 알아둘 구현 방식
- `AppException(ErrorCode, String)`은 코드는 그대로 두고 메시지만 바꾼다. 응답 `message`에는 이 메시지가 나간다.
- `ErrorCode(ErrorCode, String)` 생성자는 정의되어 있지만 쓰는 곳이 없다.
- `Sha256Hasher`는 소문자 hex 64자를 만든다. 멱등 기록의 지문 컬럼 길이(64)와 맞춰져 있다.

## 테스트 기준
오류 매핑을 바꾸면 컨트롤러 단위(MockMvc standalone + `GlobalControllerAdvice`)로 아래를 확인한다.
- `AppException` 코드와 메시지
- 검증 메시지
- 본문 파싱 실패
- 쿼리 record 생성자 예외(현재 500 → 고친 뒤 400)
- 낙관적 락 → 409
