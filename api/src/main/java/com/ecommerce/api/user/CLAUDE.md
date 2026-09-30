# user — 가입, 프로필, 정보 수정, 탈퇴

## 담당 범위
- 담당하는 것:
  - `User`(`users` 테이블, 숨김 삭제, 역할 `BUYER`/`SELLER`/`ADMIN`, 이메일 유니크)
  - 가입, 프로필(구매자면 최근 리뷰 5개와 총 개수), 관리자 회원 목록, 닉네임 변경, 비밀번호 변경, 탈퇴
  - 다른 도메인이 쓰는 사용자 조회(`getUserNotDeleted`)
- 담당하지 않는 것:
  - 로그인과 세션(auth)
  - 판매자 상품 일괄 삭제의 세부 절차(`ProductDeletionService.deleteAllBySeller` 호출만 함)
  - 리뷰 조회 쿼리(`ReviewService` 호출)
- 관리자 계정은 API로 만들지 않는다. 운영자가 DB에 직접 넣는다.

## 항상 지켜야 할 것
- 가입:
  - 이메일 중복 검사는 탈퇴자를 포함한다(`existsByEmail`). 중복이면 409(`1001`)다.
  - 비밀번호는 `PasswordEncoder`(BCrypt)로 해시한 값만 저장한다.
  - `JoinReq`가 `ADMIN` 역할과 비밀번호 확인 불일치를 거절한다.
  - 비밀번호 규칙(`docs/security.md`)은 가입의 `password`와 비밀번호 변경의 새 비밀번호에만 `@AssertTrue`로 검사한다(`support/PasswordPolicy`).
- 다른 도메인에서 사용자를 조회할 때는 `getUserNotDeleted(id)`를 쓴다. `getUser(id)`는 탈퇴자도 돌려준다.
- 탈퇴(`withdraw`)의 순서:
  1. 탈퇴하지 않은 사용자 조회
  2. 비밀번호 확인(`1003`)
  3. 판매자면 자기 상품에 `ORDERED`/`SHIPPED` 주문항목이 있는지 확인(`1004`, 403). 잠금 없는 조회라 검사 뒤에 들어온 주문은 막지 못한다(`docs/tracking/findings/auth-user.md`)
  4. 판매자면 전체 상품 삭제
  5. `deleted = true`
  
  구매자는 진행 중인 주문이 있어도 탈퇴할 수 있다(의도된 규칙). 구매자의 주문, 리뷰, 장바구니, 쿠폰은 지우지 않는다.
- 비밀번호 변경과 탈퇴에 성공하면 그 사용자의 모든 세션을 무효화해야 한다(`docs/security.md`). 현재는 컨트롤러가 현재 세션만 로그아웃시키고 다른 세션은 유지된다.
- 이메일 컬럼은 30자, 닉네임은 20자다. 가입 검증은 이메일 길이를 보지 않고, 닉네임 변경(`ModifyInfoReq`) 검증은 길이를 보지 않는다. 그래서 컬럼 길이를 넘으면 DB 오류(500)가 난다.

## 알아둘 구현 방식
- 가입일(`joinDate`)은 `createdAt`을 서울 시간대 날짜로 바꾼 값이다.
- `getUserInfoList`는 URL 인가(ADMIN)에 더해 서비스에서도 역할을 다시 확인한다(`NO_PERMISSIONS`).
- `UserService`는 `ProductDeletionService`, `ReviewService`, `OrderItemRepository`에 의존한다. 반대로 여러 도메인이 `UserService`에 의존하므로, 순환 의존이 생기지 않게 주의한다.

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 가입: 중복 이메일(탈퇴자 이메일 포함) → 409, `ADMIN` 거절.
- 판매자 탈퇴: 진행 중 주문이 있으면 403. 없으면 상품이 숨김 삭제되고 장바구니가 정리된다.
- 구매자 탈퇴: 진행 중 주문이 있어도 성공한다.
- 비밀번호 변경: 이전 비밀번호가 틀리면 401. 성공 후 새 비밀번호로 로그인된다.
- 탈퇴한 사용자는 로그인과 프로필 조회가 404/401이다.
