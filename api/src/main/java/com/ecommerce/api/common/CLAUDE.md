# common — 오류 모델, 전역 예외 처리, 공통 설정

## 담당하지 않는 것
- 도메인 규칙
- 보안 필터 단계의 오류 응답. auth가 처리한다.

## 항상 지켜야 할 것
- 새 오류는 `ErrorCode`에 추가한다. 대역, 문자열 코드, 번호 재사용 금지, 존재를 숨기는 권한 오류의 정의 방식은 `docs/standards.md`의 "입출력 계약 패턴"을 따른다. 기본 메시지는 사용자에게 보여줄 한국어 문장이다.
- 전역 핸들러(`GlobalControllerAdvice`)가 어떤 예외를 어떤 응답으로 바꾸는지는 `docs/contracts.md`의 "오류 매핑"이 원본이다. 매핑을 바꾸면 그 표도 고친다.
- `OptimisticLockingFailureException`은 무조건 주문 상태 충돌(`3004`)로 매핑된다. 다른 엔티티에 버전 락을 추가하면 분기가 필요하다.
- 요청 record 생성자 검사가 전용 코드로 응답되지 않는 결함은 `docs/tracking/findings/error-response.md`에 있다. 고칠 때는 검사를 `@AssertTrue`로 옮기고, 핸들러에서 원인 예외를 풀어 주는 방식은 쓰지 않는다.

## 알아둘 구현 방식
- `ApiResponse`는 전체가 주석 처리된 파일이다. 응답을 감싸는 래퍼는 쓰지 않고, 성공 응답은 DTO나 ID를 그대로 보낸다.
- `AppException(ErrorCode, String)`은 코드는 그대로 두고 메시지만 바꾼다. 응답 `message`에는 이 메시지가 나간다.
- `Sha256Hasher`는 소문자 hex 64자를 만든다. 멱등 기록의 지문 컬럼 길이(64)와 맞춰져 있다.

## 테스트 기준
오류 매핑을 바꾸면 컨트롤러 단위(MockMvc standalone + `GlobalControllerAdvice`)로 아래를 확인한다.
- `AppException` 코드와 메시지
- 검증 메시지
- 본문 파싱 실패
- 쿼리 record 생성자 예외(현재 500 → 고친 뒤 400)
- 낙관적 락 → 409
