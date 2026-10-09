# auth — 인증과 URL 인가

## 담당하지 않는 것
- 자원 소유자 검사. 각 도메인 서비스가 한다. 여기서는 역할만 본다.
- 세션 저장소 설정. `application-*.yaml`의 `spring.session`(Redis)이 맡는다.
- 비밀번호 변경·탈퇴 후 로그아웃. `UserController`가 `SecurityContextLogoutHandler`로 한다.

## 항상 지켜야 할 것
- 새 엔드포인트는 반드시 `adminMatchers` / `allMatchers` / `buyerMatchers` / `sellerMatchers` / `userMatchers` 중 하나에 넣는다.
  - 규칙은 **위에서부터 처음 맞는 것**이 적용된다. 순서는 admin → 공개 → buyer → seller → 로그인이다.
  - 예를 들어 `PATCH /products/{*path}`(SELLER)는 `/products/inventory`와 `/products/{id}/images`까지 포함한다. 더 넓은 패턴을 위에 추가하면 아래 규칙이 가려진다.
  - 어느 목록에도 넣지 않으면 "로그인한 사용자 누구나"가 된다. 빠뜨리면 `SecurityMatcherCoverageTest`가 실패한다.
- `CustomUserDetailsService`는 사용자가 없으면 `AppException`이 아니라 `UsernameNotFoundException`을 던진다. 그래야 Spring Security가 없는 계정에도 비밀번호 비교 시간을 맞추고(계정 존재 여부를 숨김) 내부 오류 로그를 남기지 않는다.
- `LoadTestAuthenticationFilter`는 `@Profile("loadtest")` + `@ConditionalOnProperty(app.loadtest.auth.enabled=true)`일 때만 빈이 생긴다.
  - 프로필 조건과 설정 조건 중 어느 것도 약하게 만들면 안 된다.
  - 비밀값 검사(`validate()`)의 기준은 `docs/security.md`에 있다. `application-loadtest.yaml`의 `${LOADTEST_AUTH_SECRET:}` 빈 기본값을 지우면 환경변수 누락이 빈 값 검사를 통과한다(`${LOADTEST_AUTH_SECRET}` 문자열이 비밀값이 된다). 오류 메시지에 비밀값을 넣지 않는다.
  - CSRF 예외는 `X-LoadTest-User-Id` 헤더가 있는 요청에만 적용되고, 필터 빈이 있을 때만 설정된다. 그래서 헤더가 있으면 세션으로 이미 인증된 요청이어도 비밀값부터 검사한다.
- 인증 실패와 권한 부족에는 애플리케이션 오류 형식의 본문이 없다. 401(`HttpStatusEntryPoint`)은 본문이 비어 있고, 403과 부하테스트 필터의 401(`sendError`)은 `/error`로 넘어가 Spring Boot 기본 오류 JSON이 나간다.
- `CustomUserDetails`는 Java 직렬화로 Redis 세션에 저장된다(`serialVersionUID = 1L`). 필드를 바꾸면 배포 후 기존 세션을 역직렬화하지 못해 요청이 실패하거나 로그아웃된다. 이런 배포는 전원 재로그인을 감수하고, 필요하면 배포하면서 `ecommerce:session*` 키를 비운다.
- `getUserRole()`은 권한 목록의 첫 번째 `ROLE_` 값을 `UserRole`로 바꾼다. 사용자는 역할을 하나만 가진다는 전제다.

## 알아둘 구현 방식
- 로그인·로그아웃은 보안 필터가 처리해서 springdoc이 찾지 못한다. API 명세에는 `AuthOpenApiConfig`가 직접 넣으므로, 필터의 경로나 응답을 바꾸면 그 파일도 고친다.
- API 명세와 Swagger UI 경로는 springdoc을 켠 프로필(local)에서만 따로 만든 보안 체인(`apiDocsSecurityFilterChain`)이 로그인 없이 연다.
- 로그인 필터는 폼 로그인을 끄고 `addFilterAt(…, UsernamePasswordAuthenticationFilter.class)`로 넣었다. 성공하면 `HttpSessionSecurityContextRepository`로 세션에 저장한다. 세션 전략이 붙지 않아 생기는 문제는 `docs/tracking/findings/auth-user.md`에 있다.
- 세션 쿠키 이름은 Spring Session 기본값인 `SESSION`이다. 로그아웃 설정의 `deleteCookies("JSESSIONID")`는 아무것도 지우지 않는다. 로그아웃이 동작하는 것은 세션 무효화 때문이고, 쿠키 만료는 Spring Session이 처리한다. 쿠키 이름에 기대는 코드를 만들지 않는다.

## 테스트 기준
이 패키지를 바꾸면 아래를 테스트한다.
- 역할 매트릭스: 각 matcher 그룹의 대표 경로를 비로그인(401), 다른 역할(403), 맞는 역할로 호출한다.
- CSRF 없이 POST하면 403이다.
- 로그인: 성공 200, 틀린 비밀번호·탈퇴 사용자·없는 이메일·빈 값·JSON이 아닌 본문·JSON `null` 본문은 401.
- 부하테스트 필터: 프로필이나 설정이 꺼져 있으면 헤더를 무시한다. 비밀값이 틀리면 401, 탈퇴한 사용자면 401이다. 켰는데 비밀값이 부적합하면 기동이 실패한다(실제 `application-loadtest.yaml`을 읽어 확인한다).
