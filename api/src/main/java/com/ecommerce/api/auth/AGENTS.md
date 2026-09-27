# auth — 인증과 URL 인가

## 담당 범위
- 담당하는 것:
  - `SecurityConfig`: 두 개의 필터 체인. 관리 포트는 전부 허용, 서비스 포트는 역할별 matcher.
  - `JsonUsernamePasswordAuthenticationFilter`: `POST /auth/login` JSON 로그인
  - 로그인 성공·실패 핸들러(200/401, 본문 없음)
  - `CustomUserDetails`: 세션 principal. 사용자 ID, 이메일, 비밀번호 해시, `ROLE_*` 하나.
  - `CustomUserDetailsService`: 탈퇴하지 않은 사용자만 로드
  - `LoadTestAuthenticationFilter`: 부하테스트용 헤더 인증
  - CSRF 토큰 조회 API
- 담당하지 않는 것:
  - 자원 소유자 검사. 각 도메인 서비스가 한다. 여기서는 역할만 본다.
  - 세션 저장소 설정. `application-*.yaml`의 `spring.session`(Redis)이 맡는다.
  - 비밀번호 변경·탈퇴 후 로그아웃. `UserController`가 `SecurityContextLogoutHandler`로 한다.

## 항상 지켜야 할 것
- 새 엔드포인트는 반드시 `adminMatchers` / `allMatchers` / `buyerMatchers` / `sellerMatchers` / `userMatchers` 중 하나에 넣는다.
  - 규칙은 **위에서부터 처음 맞는 것**이 적용된다. 순서는 admin → 공개 → buyer → seller → 로그인이다.
  - 예를 들어 `PATCH /products/{*path}`(SELLER)는 `/products/inventory`와 `/products/{id}/images`까지 포함한다. 더 넓은 패턴을 위에 추가하면 아래 규칙이 가려진다.
  - 어느 목록에도 넣지 않으면 "로그인한 사용자 누구나"가 된다.
- `LoadTestAuthenticationFilter`는 `@Profile("loadtest")` + `@ConditionalOnProperty(app.loadtest.auth.enabled=true)`일 때만 빈이 생긴다.
  - 프로필 조건과 설정 조건 중 어느 것도 약하게 만들면 안 된다.
  - 비밀값이 빈 문자열이면 기동이 실패한다(`validate()`의 `isBlank`).
  - 하지만 `LOADTEST_AUTH_SECRET`을 아예 설정하지 않으면 비밀값은 문자 그대로의 `${LOADTEST_AUTH_SECRET}`가 되어 검사를 통과한다. 검사를 보강하려면 `${`로 시작하는 값도 거절한다.
  - CSRF 예외는 `X-LoadTest-User-Id` 헤더가 있는 요청에만 적용되고, 필터 빈이 있을 때만 설정된다.
- 인증 실패와 권한 부족에는 애플리케이션 오류 형식(`code` 필드)의 본문이 없다.
  - 401(`HttpStatusEntryPoint`)은 본문이 비어 있다.
  - 403과 부하테스트 필터의 401(`sendError`)은 `/error`로 넘어가서 Spring Boot 기본 오류 JSON(`timestamp`, `status`, `error`, `path`)이 나간다.
- `CustomUserDetails`는 Java 직렬화로 Redis 세션에 저장된다. 필드를 바꾸면 배포 후 기존 세션을 역직렬화하지 못한다. `serialVersionUID`와 호환성을 함께 판단한다.
  - 이 객체는 `passwordHash`를 들고 있고 `CredentialsContainer`를 구현하지 않는다. 그래서 인증이 끝난 뒤에도 BCrypt 해시가 지워지지 않고 모든 세션에 함께 저장된다.
- `getUserRole()`은 권한 목록의 첫 번째 `ROLE_` 값을 `UserRole`로 바꾼다. 사용자는 역할을 하나만 가진다는 전제다.

## 알아둘 구현 방식
- 로그인 필터는 폼 로그인을 끄고 `addFilterAt(…, UsernamePasswordAuthenticationFilter.class)`로 넣었다. 성공하면 `HttpSessionSecurityContextRepository`로 세션에 저장한다.
  - 이렇게 직접 등록한 필터에는 폼 로그인에 기본으로 붙는 세션 전략(세션 ID 교체, CSRF 토큰 교체)이 적용되지 않는다. 필터 기본값이 `NullAuthenticatedSessionStrategy`이기 때문이다.
  - 세션 고정 보호가 필요하면 필터에 세션 전략을 직접 설정해야 한다.
- 세션의 사용자 정보는 로그인 시점 값이다. 탈퇴나 비밀번호 변경은 요청한 세션만 무효화한다.
- 세션 쿠키 이름은 Spring Session 기본값인 `SESSION`이다. 로그아웃 설정의 `deleteCookies("JSESSIONID")`는 아무것도 지우지 않는다. 쿠키 만료는 Spring Session이 세션 무효화 때 처리한다.
- CORS는 `WebConfig`의 `http://localhost:3000`(쿠키 포함)을 `.cors(withDefaults())`로 쓴다.

## 테스트 기준(현재 없음)
- 역할 매트릭스: 각 matcher 그룹의 대표 경로를 비로그인(401), 다른 역할(403), 맞는 역할로 호출한다.
- CSRF 없이 POST하면 403이다.
- 로그인: 성공 200, 틀린 비밀번호·탈퇴 사용자·빈 값·JSON이 아닌 본문은 401.
- 부하테스트 필터: 프로필이나 설정이 꺼져 있으면 헤더를 무시한다. 비밀값이 틀리면 401, 탈퇴한 사용자면 401이다.
