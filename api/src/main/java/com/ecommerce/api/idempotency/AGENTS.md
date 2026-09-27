# idempotency — 멱등 키 기록

## 담당 범위
- 담당하는 것:
  - `IdempotencyRecord`(유니크 `user_id + scope + idempotency_key`)
  - 선점·재응답 판정(`inspectOrClaim`), 성공 표시(`markSucceeded`), 처리 중 정리(`deleteProcessing`)
  - 키 형식 검증(`IdempotencyKeyValidator`)
  - 지문 생성 인터페이스(`RequestFingerprintGenerator<T>`)
- 담당하지 않는 것:
  - 트랜잭션을 어떻게 나눌지와 실제 업무 처리. 호출하는 래퍼 서비스(현재 주문 생성)가 맡는다.
  - 요청별 지문의 구체적인 내용. 각 도메인의 `…FingerprintGenerator`가 만든다.

## 항상 지켜야 할 것
- 서비스 메서드는 모두 `Propagation.MANDATORY`다. 호출하는 쪽이 트랜잭션을 열어야 한다.
- `inspectOrClaim`의 판정 순서:
  1. 기록이 없으면 `PROCESSING`으로 저장(`saveAndFlush`)하고 `Claimed`를 돌려준다.
  2. 만료된 기록이면 삭제 + `flush`한 뒤 새로 선점한다.
  3. 지문이 다르면 `IDEMPOTENCY_KEY_CONFLICT`(409)다.
  4. 처리 중이면 `IDEMPOTENCY_REQUEST_PROCESSING`(409)이다.
  5. 성공한 기록이면 `Replay(resourceType, resourceId)`다.
  
  지문 비교를 상태 판정보다 먼저 한다. 순서를 바꾸면 다른 내용의 요청이 기존 결과로 재응답된다.
- TTL은 `PROCESSING` 5분, `SUCCEEDED` 24시간이다(성공 표시 시점부터 다시 계산).
- `markSucceeded`와 `deleteProcessing`은 `status = PROCESSING` 조건부로만 동작한다. `markSucceeded`가 1행을 바꾸지 못하면 `IllegalStateException`을 던져 본 처리까지 롤백시킨다.
- 키 형식: `null`이면 `9100`, 공백·128자 초과·`[A-Za-z0-9._:-]` 밖의 문자면 `9101`이다.
- 새 기능에 적용할 때는 `IdempotencyScope`와 `IdempotencyResourceType`에 값을 추가한다. 다른 scope끼리는 같은 키를 써도 충돌하지 않는다.

## 알아둘 구현 방식
- 동시에 같은 키로 처음 요청하면, 두 번째 요청은 유니크 위반(`DataIntegrityViolationException`)이 난다. 호출하는 쪽이 새 트랜잭션에서 `inspectOrClaim`을 한 번 더 부르면 `PROCESSING`을 보고 409가 된다.
- 만료된 기록을 지우는 배치는 없다. 같은 키가 다시 올 때만 지워진다.

## 테스트 기준
- 키 검증은 단위 테스트로 한다(null, 공백, 129자, 허용하지 않는 문자, 경계값 128자).
- 선점, 재응답, 충돌, 처리 중, 만료 후 재선점, 성공 표시 실패 시 롤백은 실제 MySQL 통합 테스트로 한다.
