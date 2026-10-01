# coupon — 쿠폰 이벤트, 선착순 발급, 주문 시 사용·복구

규칙(사용 조건, 만료, 할인 계산, 발급 수량)은 `docs/business-rules.md`의 "쿠폰"과 `docs/adr/0005-coupon-expiry-by-timestamp.md`에 있다.

## 담당하지 않는 것
- 주문 흐름에서 언제 쿠폰을 쓰고 복구하는지(order 패키지가 호출)
- 주문항목 상태
- 사용자 조회 규칙

## 항상 지켜야 할 것
- 사용 가능 판정은 `CouponIssued.isUsable` 하나만 쓴다. `expire()`와 `findAllByStatusAndExpiresAtBefore`는 쓰지 않는 흔적이다. 이것을 쓰는 일괄 작업을 만들지 않는다.
- 할인액 0원 거절은 지금 두 곳에서 `INVALID_INPUT`으로 막힌다. 0원 상품은 `calculateDiscountAmount`에서, 정률 할인이 1원 미만으로 떨어지면 `OrderItemCoupon` 생성자에서 막힌다. 전용 오류 코드(75xx)를 만들 때는 두 경로를 모두 할인 계산 직후 한 곳에서 판정하게 바꾼다. 그래야 재고 차감 전에 실패한다.
- 주문용 쿠폰 조회는 `getCoupons` → `findAllByIdInAndUserIdForUpdate`(비관적 락, 본인 쿠폰만)로 한다. 같은 ID가 중복되면 `DUPLICATED_COUPON`, 개수가 맞지 않으면 `COUPON_ISSUED_NOT_FOUND`다.
- 발급(`issue`)의 순서:
  1. Redis 캐시가 있는지와 열림 여부를 확인한다. 캐시가 없으면 `COUPON_EVENT_CLOSED`다.
  2. Lua `issueScript`를 실행한다. 이미 발급자 집합에 있으면 −1, 재고가 0 이하이면 −2, 통과하면 `DECR` + `SADD`.
  3. DB에 `saveAndFlush`한다.
  4. DB 저장에서 **어떤 예외가 나든** `compensateScript`로 Redis를 되돌린다. 되돌리지 못하는 경우는 `docs/tracking/findings/coupon.md`에 있다.
  5. 유니크 제약(`uk_coupon_issued_event_user`) 위반일 때만 `COUPON_ALREADY_ISSUED`로 바꾼다. 현재는 `DataIntegrityViolationException` 전체를 이 코드로 바꾼다(`docs/tracking/findings/coupon.md`).

  보상 없이 예외를 삼키거나, 예외 경로를 새로 추가하면서 보상을 빠뜨리면 수량이 샌다.
- 캐시 초기화(`initEventScript`)는 meta 키가 **없을 때만** 한다. 수량은 `초기 수량 − DB 발급 건수`로 넣고, 발급자 집합은 비운다. 그래서 재적재 뒤 이미 받은 사람이 다시 요청하면 Redis 단계는 통과하고 DB 유니크 제약에 걸려 보상 후 `7506`이 된다(남은 수량이 0이면 Redis 단계에서 `7507`). 1인 1장은 이 DB 유니크 `(coupon_event_id, user_id)`가 최종으로 막으므로, 이 제약을 지우면 재적재 후 중복 발급이 가능해진다.
- `UsedCouponSnapshot`은 사용 시점의 이벤트 이름, 방식, 값, 최대 할인액, 실제 할인액을 복사한다. 조회 응답은 원본 이벤트가 아니라 스냅샷을 쓴다.

## 알아둘 구현 방식
- Redis 키(TTL은 모두 이벤트 종료 + 10분):
  - `coupon:event:{id}:meta`: JSON `CouponEventCache`
  - `coupon:event:{id}:stock`: 정수
  - `coupon:event:{id}:users`: Set
- 적재 시점과 대상은 `docs/architecture.md`의 "스케줄 작업"에 있다.

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 할인 계산(정액, 정률 버림, 최대 할인액 상한, 금액 상한, 금액 0 거절)은 단위 테스트로 한다.
- `use` / `restore`: 만료 전 복구 → `ISSUED`, 만료 후 복구 → `EXPIRED`, `USED`가 아닌 쿠폰 복구 → `INVALID_COUPON_STATUS`.
- 발급은 Redis 스크립트와 DB 보상이 얽혀 있다. 통합 테스트에 Redis(Testcontainers)를 추가해야 제대로 검증할 수 있다. 현재 통합 설정은 캐시 서비스를 mock으로 둔다.
- 경계 사례: 수량 1개에 동시 요청 N개면 1명만 성공해야 한다. Redis 재적재 직후 기존 발급자가 요청하면 `7506`(남은 수량이 0이면 `7507`)이고 수량은 그대로여야 한다.
