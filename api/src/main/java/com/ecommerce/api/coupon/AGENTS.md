# coupon — 쿠폰 이벤트, 선착순 발급, 주문 시 사용·복구

## 담당 범위
- 담당하는 것:
  - `CouponEvent`(이벤트, 할인 계산)
  - `CouponIssued`(발급 쿠폰, 상태 `ISSUED`/`USED`/`EXPIRED`)
  - `OrderItemCoupon` + `UsedCouponSnapshot`(주문항목별 사용 기록)
  - Redis 캐시와 Lua 스크립트(`CouponEventCacheService`), 캐시 적재 스케줄러
- 담당하지 않는 것:
  - 주문 흐름에서 언제 쿠폰을 쓰고 복구하는지(order 패키지가 호출)
  - 주문항목 상태
  - 사용자 조회 규칙
- 여기에 없는 기능: 내 쿠폰 목록 조회, 이벤트 비활성화·수정, 만료 일괄 전환(만들지 않기로 결정함).

## 항상 지켜야 할 것
- **사용 가능 판정은 `status == ISSUED && now < expiresAt`**(`CouponIssued.isUsable`) 하나뿐이다.
  - 만료 여부는 `expiresAt`으로 판단하고, `status`만으로 판단하지 않는다.
  - `EXPIRED`는 `restore()`에서 기한이 지난 경우에만 기록된다.
  - `expire()`와 `findAllByStatusAndExpiresAtBefore`는 쓰지 않는 흔적이다. 이것을 쓰는 일괄 작업을 만들지 않는다.
- `calculateDiscountAmount(amount)`: `amount <= 0`이면 `INVALID_INPUT`이다. 결과는 `min(방식별 할인, maxDiscountAmount, amount)`이고, 정률은 정수 나눗셈(버림)이다.
- 할인액 0원이 되는 적용은 거절해야 하는 규칙이다.
  - 현재는 두 곳에서 `INVALID_INPUT`으로 막힌다. 0원 상품은 `calculateDiscountAmount`에서, 정률 할인이 1원 미만으로 떨어지면 `OrderItemCoupon` 생성자에서 막힌다.
  - 전용 오류 코드(75xx)를 만들 때는 두 경로를 모두 할인 계산 직후 한 곳에서 판정하게 바꾼다. 그래야 재고 차감 전에 실패한다.
- 주문용 쿠폰 조회는 `getCoupons` → `findAllByIdInAndUserIdForUpdate`(비관적 락, 본인 쿠폰만)로 한다.
  - 같은 ID가 중복되면 `DUPLICATED_COUPON`이다.
  - 개수가 맞지 않으면 `COUPON_ISSUED_NOT_FOUND`다.
- 발급(`issue`)의 순서:
  1. Redis 캐시가 있는지와 열림 여부를 확인한다. 캐시가 없으면 `COUPON_EVENT_CLOSED`다.
  2. Lua `issueScript`를 실행한다. 이미 발급자 집합에 있으면 −1, 재고가 0 이하이면 −2, 통과하면 `DECR` + `SADD`.
  3. DB에 `saveAndFlush`한다.
  4. DB에서 **어떤 예외가 나든** `compensateScript`로 Redis를 되돌린다. 유니크 위반이면 `COUPON_ALREADY_ISSUED`로 바꾼다.
  
  보상 없이 예외를 삼키거나, 예외 경로를 새로 추가하면서 보상을 빠뜨리면 수량이 샌다.
- 캐시 초기화(`initEventScript`)는 meta 키가 **없을 때만** 한다. 수량은 `초기 수량 − DB 발급 건수`로 넣고, 발급자 집합은 비운다. 중복 발급은 DB 유니크 `(coupon_event_id, user_id)`가 최종으로 막는다. 이 제약을 지우면 Redis 재적재 후 중복 발급이 가능해진다.
- `UsedCouponSnapshot`은 사용 시점의 이벤트 이름, 방식, 값, 최대 할인액, 실제 할인액을 복사한다. 조회 응답은 원본 이벤트가 아니라 스냅샷을 쓴다.

## 알아둘 구현 방식
- Redis 키:
  - `coupon:event:{id}:meta`: JSON `CouponEventCache`
  - `coupon:event:{id}:stock`: 정수
  - `coupon:event:{id}:users`: Set
  - TTL은 모두 이벤트 종료 + 10분이다.
- 스케줄러는 30초마다 `active && startAt <= now+10분 && endAt > now`인 이벤트를 적재한다. 이벤트를 만들 때 이미 열려 있으면 즉시 적재한다.
- 이벤트 상세 조회는 캐시가 있으면 캐시와 실시간 잔여 수량을, 없으면 DB 값과 `remainingQuantity = null`을 쓴다.
- 이벤트 시각 입력은 `yyyy-MM-dd HH:mm:ss` + `ZoneId`(기본 `Asia/Seoul`)를 `Instant`로 바꾼다.

## 테스트 기준
- 할인 계산(정액, 정률 버림, 최대 할인액 상한, 금액 상한, 금액 0 거절)은 단위 테스트로 한다.
- `use` / `restore`: 만료 전 복구 → `ISSUED`, 만료 후 복구 → `EXPIRED`, `USED`가 아닌 쿠폰 복구 → `INVALID_COUPON_STATUS`.
- 발급은 Redis 스크립트와 DB 보상이 얽혀 있다. 통합 테스트에 Redis(Testcontainers)를 추가해야 제대로 검증할 수 있다. 현재 통합 설정은 캐시 서비스를 mock으로 둔다.
- 경계 사례: 수량 1개에 동시 요청 N개면 1명만 성공해야 한다. Redis 재적재 직후 기존 발급자가 요청하면 `7506`이고 수량은 그대로여야 한다.
