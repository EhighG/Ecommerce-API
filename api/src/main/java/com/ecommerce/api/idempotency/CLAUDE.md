# idempotency — 멱등 키 기록

멱등 처리의 결정과 이유는 `docs/adr/0001-order-idempotency.md`에, 트랜잭션 구성은 `docs/architecture.md`의 "대표 흐름"에, 키 판정 결과는 `docs/business-rules.md`의 "중복 주문 방지"에 있다.

## 담당하지 않는 것
- 트랜잭션을 어떻게 나눌지와 실제 업무 처리. 호출하는 래퍼 서비스(현재 주문 생성)가 맡는다.
- 요청별 지문의 구체적인 내용. 각 도메인의 `…FingerprintGenerator`가 만든다.

## 항상 지켜야 할 것
- `inspectOrClaim`의 판정 순서:
  1. 기록이 없으면 `PROCESSING`으로 저장(`saveAndFlush`)하고 `Claimed`를 돌려준다.
  2. 만료된 기록이면 만료 조건을 건 삭제(`id`와 `expires_at <= 지금`)로 지운 뒤 새로 선점한다. 0행이면 같은 트랜잭션에서 다시 읽지 않고, 호출하는 쪽이 새 트랜잭션에서 다시 판정한다.
  3. 지문이 다르면 `IDEMPOTENCY_KEY_CONFLICT`(409)다.
  4. 처리 중이면 `IDEMPOTENCY_REQUEST_PROCESSING`(409)이다.
  5. 성공한 기록이면 `Replay(resourceType, resourceId)`다.

  지문 비교를 상태 판정보다 먼저 한다. 순서를 바꾸면 다른 내용의 요청이 기존 결과로 재응답된다.
- `markSucceeded`와 `deleteProcessing`은 `status = PROCESSING` 조건부로만 동작한다. `markSucceeded`가 1행을 바꾸지 못하면 `IllegalStateException`을 던져 본 처리까지 롤백시킨다. 성공 표시는 만료 시각을 그 시점부터 24시간 뒤로 다시 잡는다.
- 새 기능에 적용할 때는 `IdempotencyScope`와 `IdempotencyResourceType`에 값을 추가한다. 다른 scope끼리는 같은 키를 써도 충돌하지 않는다.

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 키 검증은 단위 테스트로 한다(null, 공백, 129자, 허용하지 않는 문자, 경계값 128자).
- 선점, 재응답, 충돌, 처리 중, 만료 후 재선점, 성공 표시 실패 시 롤백은 실제 MySQL 통합 테스트로 한다.
