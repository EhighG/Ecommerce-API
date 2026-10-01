# user — 가입, 프로필, 정보 수정, 탈퇴

가입·탈퇴 규칙은 `docs/business-rules.md`의 "사용자와 역할"에, 비밀번호 규칙과 세션 무효화 범위는 `docs/security.md`에 있다.

## 담당하지 않는 것
- 로그인과 세션(auth)
- 판매자 상품 일괄 삭제의 세부 절차(`ProductDeletionService.deleteAllBySeller` 호출만 함)
- 리뷰 조회 쿼리(`ReviewService` 호출)

## 항상 지켜야 할 것
- 가입
  - 이메일 중복 검사는 탈퇴자를 포함한다(`existsByEmail`).
  - `JoinReq`가 `ADMIN` 역할과 비밀번호 확인 불일치를 거절한다.
  - 비밀번호 규칙은 가입의 `password`와 비밀번호 변경의 새 비밀번호에만 `@AssertTrue`로 검사한다(`support/PasswordPolicy`).
- 다른 도메인에서 사용자를 조회할 때는 `getUserNotDeleted(id)`를 쓴다. `getUser(id)`는 탈퇴자도 돌려준다.
- 탈퇴(`withdraw`)의 순서:
  1. 탈퇴하지 않은 사용자 조회
  2. 비밀번호 확인(`1003`)
  3. 판매자면 자기 상품에 `ORDERED`/`SHIPPED` 주문항목이 있는지 확인(`1004`). 잠금 없는 조회라 검사 뒤에 들어온 주문은 막지 못한다(`docs/tracking/findings/auth-user.md`)
  4. 판매자면 전체 상품 삭제
  5. `deleted = true`

  구매자의 주문, 리뷰, 장바구니, 쿠폰은 지우지 않는다.

## 알아둘 구현 방식
- `getUserInfoList`는 URL 인가(ADMIN)에 더해 서비스에서도 역할을 다시 확인한다(`NO_PERMISSIONS`).

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 가입: 중복 이메일(탈퇴자 이메일 포함) → 409, `ADMIN` 거절.
- 판매자 탈퇴: 진행 중 주문이 있으면 403. 없으면 상품이 숨김 삭제되고 장바구니가 정리된다.
- 구매자 탈퇴: 진행 중 주문이 있어도 성공한다.
- 비밀번호 변경: 이전 비밀번호가 틀리면 401. 성공 후 새 비밀번호로 로그인된다.
- 탈퇴한 사용자는 로그인과 프로필 조회가 404/401이다.
