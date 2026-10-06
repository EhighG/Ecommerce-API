# 주문 생성·취소의 의도하지 않은 중복 요청 막기: 실제 서비스 조사

기준일: 2026-10-06

공식 문서, 표준 초안, 회사가 직접 쓴 엔지니어링 블로그만 출처로 썼다. 괄호 안 숫자는 맨 아래 [출처](#출처) 번호이고, 링크는 해당 페이지(가능하면 해당 절)로 간다. 상태 코드, 기간, 오류 코드는 원문 그대로 옮겼다. "(추론)"이라고 표시한 문장만 추론이다.

## 요약

- **한쪽이 다 하지 않는다.** 서버는 "같은 키는 한 번만 실행"을 보장하고, 무엇이 "같은 요청"인지는 클라이언트가 키로 표시한다. Shopify는 이 판단이 클라이언트 몫이라고 적는다("The onus is on the client")([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency)). AWS도 내용이 똑같은 두 요청이 정말 두 건을 원한 것일 수 있다고 본다([29](https://builder.aws.com/content/3Ev0BENTyBr0XxzRk5FDZzgNYos/making-retries-safe-with-idempotent-apis)).
- **백엔드가 하는 일**: 키와 요청 지문을 저장한다. 같은 키·같은 내용이면 저장한 결과를 다시 준다. 같은 키·다른 내용은 거절한다. 처리 중인 키는 충돌(대개 409)로 거절한다. 키 기록과 본 처리를 원자적으로 묶고, 보관 기간을 포함한 규칙을 공개한다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.5.2), [4](https://docs.stripe.com/api/idempotent_requests), [29](https://builder.aws.com/content/3Ev0BENTyBr0XxzRk5FDZzgNYos/making-retries-safe-with-idempotent-apis)).
- **클라이언트가 하는 일**: 작업 하나에 키 하나를 만들고 보내기 전에 보관한다. 결과를 모를 때(타임아웃, 연결 끊김, 5xx, 처리 중 409)는 같은 키·같은 본문으로 지수 백오프와 지터를 두고 재시도한다. 내용을 바꾸거나 새 작업을 시작할 때만 새 키를 만든다. 버튼 중복 클릭도 막는다([5](https://docs.stripe.com/error-low-level#network-errors), [16](https://docs.stripe.com/payments/accept-a-payment-deferred), [38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#best-practices-for-using-idempotency-keys)).
- **키는 대부분 클라이언트가 UUID로 만든다.** 장바구니 ID처럼 업무 객체에서 키를 끌어내는 방법도 공식 문서에 있다([5](https://docs.stripe.com/error-low-level#idempotency)). 결제처럼 단계가 여러 개인 흐름은 서버 쪽에서 결제 객체나 주문번호를 먼저 만들고, 그 ID로 확정한다. 이때 두 번째 확정은 객체 상태가 막는다([11](https://docs.stripe.com/payments/payment-intents), [26](https://docs.tosspayments.com/reference), [27](https://docs.tosspayments.com/reference/error-codes)).
- **보관 기간은 서비스마다 다르다.** Stripe v1 24시간, Shopify 24시간, Adyen 7~14일, 토스페이먼츠 15일, Stripe v2 30일이다. IETF 초안은 기간을 정해 공개하라고만 한다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.3), [5](https://docs.stripe.com/error-low-level#idempotency), [10](https://docs.stripe.com/api-v2-overview), [21](https://docs.adyen.com/development-resources/api-idempotency), [24](https://docs.tosspayments.com/reference/using-api/idempotency-key), [38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#how-idempotency-protection-is-implemented-on-the-server-side)).
- **첫 요청이 실패했을 때의 처리는 갈린다.** IETF 초안과 Stripe v1은 오류 결과도 저장해 그대로 돌려준다. Stripe v2는 실패한 요청을 같은 키로 다시 실행한다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.6), [4](https://docs.stripe.com/api/idempotent_requests), [10](https://docs.stripe.com/api-v2-overview)).
- **취소·환불은 키와 상태를 같이 쓴다.** 토스페이먼츠는 결제 취소에 멱등키를 권하면서, 이미 취소된 결제는 400 오류로 돌려준다. Adyen은 환불 합계가 캡처 금액을 넘지 못하게 막는다([26](https://docs.tosspayments.com/reference), [27](https://docs.tosspayments.com/reference/error-codes), [21](https://docs.adyen.com/development-resources/api-idempotency)).
- **"N분 안에 같은 내용이면 중복"이라는 의미 기반 판정은 피하라는 쪽이다**(AWS, Shopify). 예외로 PayPal의 한 결제수단이 몇 초 창을 쓰고, 여러 결제사가 주문번호·송장 ID 고유성을 검사한다([29](https://builder.aws.com/content/3Ev0BENTyBr0XxzRk5FDZzgNYos/making-retries-safe-with-idempotent-apis), [38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency), [18](https://developer.paypal.com/api/rest/reference/orders/v2/errors/)).

## 질문별 정리

### 1. 키는 누가 만드나

**클라이언트가 무작위 키를 만든다.** 가장 흔한 방식이다.
- IETF 초안: "It is RECOMMENDED that a UUID [RFC4122] or a similar random identifier be used as an idempotency key." 고유성 규칙은 서버가 정하고 클라이언트가 지킨다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.2)). 키는 "the user's intent is to only perform this action once"를 나타낸다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.5.1)).
- 같은 방식: Stripe("A client generates an idempotency key", V4 UUID, 최대 255자, 개인 식별 정보 금지)([4](https://docs.stripe.com/api/idempotent_requests)), Adyen([21](https://docs.adyen.com/development-resources/api-idempotency)), PayPal([17](https://developer.paypal.com/api/rest/reference/idempotency/)), Square([22](https://developer.squareup.com/docs/build-basics/common-api-patterns/idempotency)), 토스페이먼츠("UUID와 같이 충분히 무작위적인 고유 값")([24](https://docs.tosspayments.com/reference/using-api/idempotency-key)), AWS EC2 `ClientToken`([31](https://docs.aws.amazon.com/ec2/latest/devguide/ec2-api-idempotency.html)), Google AIP-155 `request_id`([33](https://google.aip.dev/155)), Shopify(레거시 REST는 본문 `unique_token`)([37](https://shopify.dev/docs/api/usage/idempotent-requests), [39](https://shopify.dev/docs/api/admin-rest/usage/idempotent-requests)).

**클라이언트가 업무 객체에서 키를 끌어낸다.**
- Stripe: "Derive the key from a user-attached object, like the ID of a shopping cart. This provides a relatively straightforward way to protect against double submissions."([5](https://docs.stripe.com/error-low-level#idempotency)) PaymentIntent 중복 생성을 막는 키도 "typically based on the ID that you associate with the cart or customer session"이다([11](https://docs.stripe.com/payments/payment-intents)).
- Shopify: 백그라운드 작업은 작업 ID와 요청 변수로 UUID v5를 만든다. 이런 키는 저장하지 않아도 다시 만들 수 있다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#generating-idempotency-keys)).

**서버 쪽에서 객체(결제 의도, 세션, 주문번호)를 먼저 만들고 그 ID로 확정한다.** Stripe PaymentIntent, 토스페이먼츠 orderId가 그렇다. 5번에서 본다([11](https://docs.stripe.com/payments/payment-intents), [28](https://docs.tosspayments.com/guides/v2/payment-widget/integration)). 비슷하게 Google AIP-133은 클라이언트가 자원 ID를 정해 만들게 하고, 같은 ID로 다시 만들면 `ALREADY_EXISTS`를 준다([35](https://google.aip.dev/133)).

**언제 무엇을 쓰나.** 문서에 드러난 쓰임은 이렇다. 일반 API 재시도는 무작위 키([4](https://docs.stripe.com/api/idempotent_requests)), 이중 제출 방지는 장바구니 ID 파생 키([5](https://docs.stripe.com/error-low-level#idempotency)), 인증·리다이렉트가 끼는 결제는 서버 객체([11](https://docs.stripe.com/payments/payment-intents), [28](https://docs.tosspayments.com/guides/v2/payment-widget/integration)), 백그라운드 작업은 결정적 키다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#generating-idempotency-keys)). (추론) 서버 객체 방식은 사용자가 페이지를 떠났다 돌아와도 ID로 같은 객체를 찾는다. 무작위 키 방식은 클라이언트가 키를 잃으면 보호도 잃는다.

### 2. 키 범위, 고유성, 보관 기간

- **규칙**: IETF 초안은 "The resource SHOULD define such expiration policy and publish it in the documentation."라고만 하고 기간 값은 정하지 않는다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.3)). 이 초안의 최신판은 -07(2025-10-15)이고, 2026-04-18에 만료됐다. RFC가 아니다([2](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)).
- **기간**: Stripe v1은 "keys expire out of the system after 24 hours"([5](https://docs.stripe.com/error-low-level#idempotency))이고, 지워진 뒤 같은 키는 새 요청이다([4](https://docs.stripe.com/api/idempotent_requests)). Stripe v2는 30일([10](https://docs.stripe.com/api-v2-overview)). Shopify는 "**24 hours**"이고 그 뒤는 "will be treated as separate operations"([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#how-idempotency-protection-is-implemented-on-the-server-side)). Adyen은 "7 to 14 days"([21](https://docs.adyen.com/development-resources/api-idempotency)). 토스페이먼츠는 "처음 요청에 사용한 날부터 15일간 유효"([24](https://docs.tosspayments.com/reference/using-api/idempotency-key)). PayPal은 API마다 다르다고만 한다([17](https://developer.paypal.com/api/rest/reference/idempotency/)). Shopify가 결제 앱을 부르는 Payments Apps API는 "Idempotency keys don't expire."([40](https://shopify.dev/docs/apps/build/payments/considerations#idempotency))
- **범위**: Stripe는 계정 안에서 고유하다([5](https://docs.stripe.com/error-low-level#idempotency)). Adyen은 회사 계정 단위이고 리전끼리는 검사하지 않는다([21](https://docs.adyen.com/development-resources/api-idempotency)). 토스페이먼츠는 "멱등키와 API 키, API 주소, HTTP 메서드 조합"으로 판정한다([24](https://docs.tosspayments.com/reference/using-api/idempotency-key)). PayPal은 요청마다, API 호출 종류마다 고유해야 한다([17](https://developer.paypal.com/api/rest/reference/idempotency/)). EC2는 리전 또는 가용 영역 단위다([31](https://docs.aws.amazon.com/ec2/latest/devguide/ec2-api-idempotency.html)).
- **기간을 정하는 기준**: AWS는 EC2라면 자원 수명에, 늦게 도착하는 요청("late arriving requests")이 다 왔다고 볼 만한 간격을 더하면 된다고 본다([29](https://builder.aws.com/content/3Ev0BENTyBr0XxzRk5FDZzgNYos/making-retries-safe-with-idempotent-apis)). AIP-155는 "any reasonable timeframe"을 허용한다([33](https://google.aip.dev/155)).

### 3. 서버 동작

**같은 키 + 같은 내용.** 저장한 결과를 다시 준다.
- IETF 초안: "respond with the result of the previously completed operation, success or an error."([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.6)) Stripe v1은 "including `500` errors"까지 같은 결과를 주고([4](https://docs.stripe.com/api/idempotent_requests)), 재생 응답에 `Idempotent-Replayed: true`를 붙인다([5](https://docs.stripe.com/error-low-level#idempotency)). Square는 200과 기존 자원을 준다([23](https://developer.squareup.com/docs/build-basics/general-considerations/using-rest-api)).
- 원래 응답 대신 현재 상태를 주는 곳도 있다. PayPal은 "the status of a request at the current time"을 준다([17](https://developer.paypal.com/api/rest/reference/idempotency/)). AIP-155와 Shopify도 그사이 자원이 바뀌었으면 현재 상태로 응답할 수 있다([33](https://google.aip.dev/155), [38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#how-idempotency-protection-is-implemented-on-the-server-side)).

**같은 키 + 다른 내용.** 모두 거절하지만 상태 코드는 제각각이다. 서비스별 값은 [서비스별 비교](#서비스별-비교) 표에 있다.
- IETF 초안은 422([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.7)), Square는 400 `IDEMPOTENCY_KEY_REUSED`다([23](https://developer.squareup.com/docs/build-basics/general-considerations/using-rest-api)). Stripe는 오류 타입 `idempotency_error`만 문서에 있고([7](https://docs.stripe.com/api/errors)), HTTP 상태 코드는 찾지 못했다(미확인).
- Shopify는 서버가 매개변수로 지문을 만든다. 지문에서 빠지는 필드는 문서에 적고, 필드 순서가 지문을 바꿀 수 있다고 경고한다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency-errors)).

**처리 중인 키로 또 요청.** 대부분 409로 거절하고, 같은 키로 다시 보내게 한다. 서비스별 값은 비교 표에 있다.
- IETF 초안 409([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.7)). Stripe는 이 요청의 결과를 저장하지 않으므로 다시 보낼 수 있다([4](https://docs.stripe.com/api/idempotent_requests)).
- 토스페이먼츠 409 `IDEMPOTENT_REQUEST_PROCESSING`: "이 에러가 돌아오면 다시 한번 요청해서 응답을 확인하세요."([24](https://docs.tosspayments.com/reference/using-api/idempotency-key)) Shopify는 백오프 뒤 같은 키로 재시도하라고 한다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency-errors)).

**무엇을 저장하나.**
- 키와 지문. 지문은 본문 전체나 일부 필드의 체크섬, 필드 값 비교, 요청 서명 등으로 만든다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.4)). Stripe는 첫 응답의 상태 코드와 본문, 비교할 매개변수를 둔다([4](https://docs.stripe.com/api/idempotent_requests)).
- AWS: 키 기록과 그 요청의 모든 변경은 "atomic, consistent, isolated, and durable (ACID) operation"이어야 한다([29](https://builder.aws.com/content/3Ev0BENTyBr0XxzRk5FDZzgNYos/making-retries-safe-with-idempotent-apis)).
- Stripe는 매개변수 검증 실패나 동시 실행 충돌처럼 실행이 시작되지 않은 요청은 저장하지 않는다([4](https://docs.stripe.com/api/idempotent_requests)). 429와 키 없는 401도 멱등 계층보다 앞에서 처리된다([5](https://docs.stripe.com/error-low-level#content-errors)).

**첫 요청이 실패했을 때.** 출처끼리 갈린다.
- 오류도 저장해 재생: IETF 초안([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.6))과 Stripe v1이다. Stripe v1에서는 고쳐서 다시 보낼 때 새 키를 쓴다("the safest strategy where `4xx` errors are concerned is to always generate a new idempotency key")([5](https://docs.stripe.com/error-low-level#content-errors)). 500은 "indeterminate"이고, 새 키로 재시도하는 것은 "we advise against it because the original key may have produced side effects."([5](https://docs.stripe.com/error-low-level#server-errors))
- 실패하면 다시 실행: Stripe v2는 "re-executes the failed requests"([10](https://docs.stripe.com/api-v2-overview)). Stripe 블로그도 "if the previous operation was successfully rolled back by way of an ACID database, it'll be safe to retry it wholesale."라고 한다([6](https://stripe.com/blog/idempotency)).
- 그 밖: 토스페이먼츠는 오류가 났을 때 "멱등키를 변경해서 동일한 요청을 재시도하는 것은 위험"하니 원인부터 확인하라고 한다([24](https://docs.tosspayments.com/reference/using-api/idempotency-key)). Adyen은 `transient-error: true`일 때만 같은 키로 재시도하라고 한다([21](https://docs.adyen.com/development-resources/api-idempotency)).

### 4. 클라이언트 규칙

**같은 키를 다시 쓸 때와 새 키를 만들 때.** Shopify가 가장 구체적이다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#when-to-generate-a-new-key)).
- 새 키: 새 의도의 작업, 요청 매개변수 변경.
- 같은 키: 백엔드 처리 실패·네트워크 실패·타임아웃 뒤 재시도, `IDEMPOTENCY_CONCURRENT_REQUEST` 뒤 재시도, 완료 여부를 모르는 작업의 재전송.
- 요약: "If you receive a successful response from the backend, generate a new key ... If you receive a failed response from the backend, reuse the same key".
- 흔한 실수로 "generating a new random UUID every time you call the mutation"을 든다. 중복 요청의 예는 더블클릭, 타임아웃 뒤 재시도, 작업 재시도다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency)). 토스페이먼츠 블로그는 "따닥"을 든다([25](https://docs.tosspayments.com/blog/what-is-idempotency)).

**결과를 모르는 응답.** 같은 키·같은 본문으로 재시도한다.
- Stripe 블로그는 실패를 연결 실패, 처리 도중 실패, 처리 후 응답 유실로 나눈다. 연결조차 못 맺은 경우는 그냥 재시도해도 된다. 하지만 연결이 중간에 끊기면 성공했는지 알 수 없다([6](https://stripe.com/blog/idempotency)).
- Stripe: 소켓 오류나 타임아웃이면 "retry such requests with the same idempotency keys and the same parameters until they're able to receive a result from the server."([5](https://docs.stripe.com/error-low-level#network-errors)) 서버는 `Stripe-Should-Retry` 헤더로 재시도 여부를 알려 준다([5](https://docs.stripe.com/error-low-level#should-retry)). PayPal도 "network timeouts or HTTP `5xx`"는 ID 보관 기간 안에서 재시도하라고 한다([17](https://developer.paypal.com/api/rest/reference/idempotency/)).
- 결과가 확정된 응답은 그대로 재시도하지 않는다. EC2는 200과 4xx는 재시도하지 않고, 5xx는 백오프를 두고 재시도한다([31](https://docs.aws.amazon.com/ec2/latest/devguide/ec2-api-idempotency.html#recommended-actions)). IETF 초안: "Clients MUST correct the requests (with the exception of 409 where no correction is required) before performing a retry operation"([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.7)).
- RFC 9110은 비멱등 요청을 자동 재시도하려면 "some means to know that the request semantics are actually idempotent"가 있어야 한다고 한다([3](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2)). 멱등 키가 그 수단이다.

**재시도 간격.** Stripe 블로그는 2^n 지수 백오프에 무작위 지터를 섞으라고 한다([6](https://stripe.com/blog/idempotency)). AWS는 상한 있는 백오프, 횟수 제한, 지터, 스택의 한 지점에서만 재시도를 권한다. "APIs with side effects aren't safe to retry unless they provide idempotency."([30](https://builder.aws.com/content/3EumjoZascWd1oZiEgL8ORlv3qE/timeouts-retries-and-backoff-with-jitter)) Google AIP-194는 트랜잭션 성격의 요청을 자동 재시도하지 말고 애플리케이션 단에서 처음부터 다시 하라고 한다([34](https://google.aip.dev/194)).

**키 보관.** Shopify: "**Before sending the request**, you should generally persist the idempotency key along with the operation intent." 웹·모바일은 키를 화면 수명에 묶는다. 예제는 폼이 열릴 때 키를 만들고, 성공하면 새로 만들고, 실패하면 그대로 둔다. 그래서 성공 응답 전의 더블클릭은 모두 같은 키를 쓴다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#storing-idempotency-keys)). 새로고침 뒤에도 키를 유지하는 방법(브라우저 저장소 등)은 1차 출처에서 찾지 못했다(미확인).

**이중 제출 막기.** Stripe 예제 코드는 버튼이 이미 비활성이면 바로 반환하고("Prevent multiple form submissions"), 처리 중에는 비활성으로 두고, 오류가 나면 다시 활성으로 돌린다([16](https://docs.stripe.com/payments/accept-a-payment-deferred)). 새로고침은 POST 뒤 303으로 결과 페이지에 보내 막는다. MDN: "Used to redirect after a PUT or a POST, so that refreshing the result page doesn't re-trigger the operation."([44](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Redirections), [3](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4.4))

**끝내 결과를 모를 때.** 토스페이먼츠는 상점 주문번호로 결제를 조회하는 `GET /v1/payments/orders/{orderId}`를 둔다([26](https://docs.tosspayments.com/reference)). Stripe는 생성 시 metadata에 로컬 식별자를 넣고 웹훅으로 대조하라고 한다([5](https://docs.stripe.com/error-low-level#server-errors)). Shopify는 같은 키 재시도에 NOT_FOUND가 오면 새 키로 다시 만들지 말고 상태부터 확인하라고 한다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency-errors)).

### 5. 서버 쪽 ID를 먼저 만들고 나중에 확정하는 방식

- **토스페이먼츠**: 상점이 orderId를 만들어 금액과 함께 서버에 먼저 저장한다([28](https://docs.tosspayments.com/guides/v2/payment-widget/integration)). 결제 인증 뒤 서버가 paymentKey, orderId, amount로 `POST /v1/payments/confirm`을 부르고, 10분 안에 부르지 않으면 결제가 만료된다. orderId는 "각 주문마다 고유한 값"이어야 한다([26](https://docs.tosspayments.com/reference)). 승인 API는 `ALREADY_PROCESSED_PAYMENT`, `ALREADY_PROCESSING_REQUEST`를 준다. 카드 번호 결제, 가상계좌 발급 등은 이미 쓴 주문번호에 `DUPLICATED_ORDER_ID`를 준다([27](https://docs.tosspayments.com/reference/error-codes)).
- **Stripe PaymentIntent**: "Each PaymentIntent typically correlates with a single shopping cart or customer session". 장점으로 "No double charges", "No idempotency key issues"를 든다([11](https://docs.stripe.com/payments/payment-intents)). 결제가 실패하면 `requires_payment_method`로 돌아가 같은 객체로 다시 시도한다([12](https://docs.stripe.com/payments/paymentintents/lifecycle)). 취소하면 이후 작업은 모두 오류다([13](https://docs.stripe.com/api/payment_intents/cancel)).
- **Stripe Checkout Session**: "We recommend creating a new Session each time your customer attempts to pay."([14](https://docs.stripe.com/api/checkout/sessions)) 주문 이행 함수는 같은 세션으로 "might be called multiple times, possibly concurrently"이므로 한 번만 이행하게 만든다([15](https://docs.stripe.com/checkout/fulfillment)).
- **Shopify**: 카트를 만들고 `checkoutUrl`로 체크아웃에 보낸다([43](https://shopify.dev/docs/storefronts/headless/building-with-the-storefront-api/cart/manage)). "Shopify automatically deletes the cart when the customer completes their checkout."([42](https://shopify.dev/docs/storefronts/headless/building-with-the-storefront-api/cart)) 결제 앱에는 결제 시도마다 `id`를 멱등 키로 보낸다([41](https://shopify.dev/docs/apps/build/payments/request-reference)).
- **PayPal Orders v2**: 주문을 만든 뒤 캡처한다. 두 번째 캡처는 `ORDER_ALREADY_CAPTURED`("only one capture per order is allowed")다([18](https://developer.paypal.com/api/rest/reference/orders/v2/errors/)).

**책임이 어떻게 옮겨가나.** 두 번째 확정은 객체 상태가 막는다([12](https://docs.stripe.com/payments/paymentintents/lifecycle), [18](https://developer.paypal.com/api/rest/reference/orders/v2/errors/), [27](https://docs.tosspayments.com/reference/error-codes)). 하지만 객체를 만드는 단계의 중복은 여전히 키로 막으라고 한다([11](https://docs.stripe.com/payments/payment-intents)). (추론) 클라이언트는 "결과 모름"을 키 재전송 대신 객체 ID 조회로 풀 수 있다. 대신 서버는 부작용 없는 준비 단계와 부작용 있는 확정 단계를 나누고, 확정을 상태 전이로 만들어야 한다.

### 6. 취소·환불

**키로 막기.** 토스페이먼츠 결제 취소: "멱등키를 요청 헤더에 추가하면 중복 취소 없이 안전하게 처리됩니다." 부분 취소 금액 `cancelAmount`도 받는다([26](https://docs.tosspayments.com/reference)). Stripe는 모든 POST가 키를 받는다([4](https://docs.stripe.com/api/idempotent_requests)). Shopify 결제 앱은 환불·캡처·취소 시도마다 `id`가 멱등 키다([41](https://shopify.dev/docs/apps/build/payments/request-reference)).

**상태로 막기.**
- 토스페이먼츠: 이미 취소된 결제는 400 `ALREADY_CANCELED_PAYMENT`([27](https://docs.tosspayments.com/reference/error-codes)). Stripe: 이미 환불된 charge는 `charge_already_refunded`([8](https://docs.stripe.com/error-codes)). Adyen: 기본 규칙상 "the total refunded value cannot exceed the captured amount"([21](https://docs.adyen.com/development-resources/api-idempotency)).
- Google AIP-135: 없는 자원 삭제는 `NOT_FOUND`이지만, `allow_missing`이면 no-op 성공이다([36](https://google.aip.dev/135)).
- RFC 9110: 멱등성은 서버에 미치는 효과로 정하고, 응답은 달라도 된다("though the response might differ")([3](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2)).

정리하면 결제사는 취소에 키와 상태를 함께 쓴다. "이미 취소됨"은 성공이 아니라 오류 코드로 주는 곳이 많다([8](https://docs.stripe.com/error-codes), [27](https://docs.tosspayments.com/reference/error-codes)). 성공으로 주는 방식은 AIP-135의 `allow_missing`이 가장 가까운 예다([36](https://google.aip.dev/135)).

### 7. 의미 기반 중복 판정

**피하라는 쪽**
- AWS: 짧은 간격의 똑같은 DynamoDB 테이블 생성은 중복으로 봐도 될지 모르지만, EC2라면 "It's possible that the caller actually wants two identical EC2 instances." 그래서 호출자가 주는 요청 ID를 쓴다([29](https://builder.aws.com/content/3Ev0BENTyBr0XxzRk5FDZzgNYos/making-retries-safe-with-idempotent-apis)).
- Shopify: 변수가 완전히 같아도 "intended to result in a separate operation"이면 중복이 아니다. 공급사 두 곳에서 각각 재고 +2를 받은 경우를 예로 든다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency)).
- IETF 초안의 지문은 키와 "in conjunction with" 쓰는 것이다. 키 없이 지문만으로 판정하라는 내용은 없다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.4)).

**쓰는 사례**
- PayPal `PUI_DUPLICATE_ORDER`: Pay Upon Invoice 주문이 "with the same payload has already been successfully processed in the last few seconds"면 거절한다. `DUPLICATE_INVOICE_ID`: 계정 설정이 송장 ID 고유를 요구하면 거절한다([18](https://developer.paypal.com/api/rest/reference/orders/v2/errors/)).
- 토스페이먼츠 `DUPLICATED_ORDER_ID`: 주문번호 고유성 검사다([27](https://docs.tosspayments.com/reference/error-codes)).
- AWS SQS FIFO: 5분 간격 안에서 본문 SHA-256으로 중복을 거른다. 주문이 아닌 메시지 큐의 예다([32](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-exactly-once-processing.html)).

찾은 사례는 대부분 업무 식별자(주문번호, 송장 ID)의 고유성 검사다. "같은 사용자 + 같은 상품 + N분" 같은 시간 창 판정을 일반 주문 API에 권하는 1차 출처는 찾지 못했다(미확인).

## 서비스별 비교

| 서비스 | 키 생성 주체 | 헤더/필드 이름 | 보관 기간 | 같은 키·다른 내용 | 처리 중 중복 | 비고 |
|---|---|---|---|---|---|---|
| IETF 초안 -07 ([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html)) | 클라이언트(UUID 권장) | `Idempotency-Key` 헤더 | 서버가 정해 공개(SHOULD) | 422 | 409 | 키 누락은 400. 2026-04-18 만료된 초안([2](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)) |
| Stripe API v1 ([4](https://docs.stripe.com/api/idempotent_requests), [5](https://docs.stripe.com/error-low-level)) | 클라이언트(V4 UUID 권장, 최대 255자) | `Idempotency-Key` 헤더 | 24시간 | `idempotency_error`([7](https://docs.stripe.com/api/errors), [9](https://docs.stripe.com/changelog/2015-09-03/reuse-idempotency-tokens-alter-error)). 상태 코드는 문서에 없음 | 409, `idempotency_key_in_use`([8](https://docs.stripe.com/error-codes)) | 첫 결과를 500까지 저장. 재생 응답에 `Idempotent-Replayed: true` |
| Stripe API v2 ([10](https://docs.stripe.com/api-v2-overview)) | 클라이언트(없으면 Stripe가 UUID 생성) | `Idempotency-Key` 헤더 | 30일 | 문서에 없음 | 문서에 없음 | 실패한 요청은 같은 키로 다시 실행 |
| PayPal ([17](https://developer.paypal.com/api/rest/reference/idempotency/)) | 클라이언트(UUID 권장, 38자 한도) | `PayPal-Request-Id` 헤더 | API마다 다름. Orders v2 레퍼런스에는 없음([20](https://developer.paypal.com/docs/api/orders/v2/)) | 문서에 없음 | 두 번째 요청이 실패할 수 있음. Orders v2는 `PREVIOUS_REQUEST_IN_PROGRESS`(409)([18](https://developer.paypal.com/api/rest/reference/orders/v2/errors/), [19](https://developer.paypal.com/api/rest/responses/)) | 원래 응답이 아니라 현재 상태를 돌려줌 |
| Adyen ([21](https://docs.adyen.com/development-resources/api-idempotency)) | 클라이언트(UUID v4, 최대 64자) | `idempotency-key` 헤더 | 7~14일 | 문서에 없음 | 422 또는 409, 코드 704 | 회사 계정 단위. `transient-error: true`면 같은 키로 재시도 |
| Square ([22](https://developer.squareup.com/docs/build-basics/common-api-patterns/idempotency), [23](https://developer.squareup.com/docs/build-basics/general-considerations/using-rest-api)) | 클라이언트(UUID) | 본문 `idempotency_key` | 문서에 없음 | 400 `IDEMPOTENCY_KEY_REUSED` | 문서에 없음 | 같은 요청이면 200과 기존 자원. API마다 다를 수 있음 |
| 토스페이먼츠 ([24](https://docs.tosspayments.com/reference/using-api/idempotency-key)) | 상점(UUID 등, 최대 300자) | `Idempotency-Key` 헤더 | 15일 | 문서에 없음(판정 조합에 본문이 없음) | 409 `IDEMPOTENT_REQUEST_PROCESSING` | 판정 단위는 키+API 키+주소+메서드. 결제 승인은 orderId 고유([26](https://docs.tosspayments.com/reference)) |
| AWS EC2 ([31](https://docs.aws.amazon.com/ec2/latest/devguide/ec2-api-idempotency.html)) | 클라이언트(최대 64 ASCII, 대소문자 구분) | `ClientToken` 파라미터 | 문서에 없음 | `IdempotentParameterMismatch` | 문서에 없음 | 리전 또는 가용 영역 단위 |
| Google AIP-155 ([33](https://google.aip.dev/155)) | 클라이언트(UUID4) | `request_id` 필드 | "any reasonable timeframe" | 문서에 없음 | 문서에 없음 | 자원이 바뀌었으면 현재 상태 응답 허용 |
| Shopify GraphQL Admin ([37](https://shopify.dev/docs/api/usage/idempotent-requests), [38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency)) | 앱(UUID v4·v7, 결정적이면 v5) | `@idempotent(key:)` 디렉티브 또는 `idempotencyKey` 인자 | 24시간 | `IDEMPOTENCY_KEY_PARAMETER_MISMATCH` | `IDEMPOTENCY_CONCURRENT_REQUEST` | 오류는 `userErrors`의 `code`로 옴 |
| Shopify Payments Apps ([40](https://shopify.dev/docs/apps/build/payments/considerations#idempotency), [41](https://shopify.dev/docs/apps/build/payments/request-reference)) | Shopify(호출하는 쪽), 결제 시도마다 | 본문 `id` | 만료 없음 | 같은 `id`는 같은 요청으로 간주 | 문서에 없음 | 환불·캡처·취소도 시도마다 `id` |
| (참고) 이 프로젝트 ([business-rules.md](../business-rules.md#중복-주문-방지)) | 클라이언트 | `Idempotency-Key` 헤더 | 성공 24시간, 처리 중 5분 | 409 `9102` | 409 `9103` | 실패하면 기록을 지워 같은 키로 다시 처리 |

## 역할 분담 정리

### 백엔드
1. 멱등 규칙(키 형식, 보관 기간, 오류 응답)을 문서로 공개한다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.5.2)).
2. 키를 계정·사용자 같은 범위 안에서 식별한다([5](https://docs.stripe.com/error-low-level#idempotency), [21](https://docs.adyen.com/development-resources/api-idempotency), [24](https://docs.tosspayments.com/reference/using-api/idempotency-key)).
3. 요청 지문을 저장하고 같은 키·다른 내용을 거절한다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.4), [4](https://docs.stripe.com/api/idempotent_requests), [38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency-errors)).
4. 끝난 요청은 저장한 결과로 다시 응답한다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.6), [4](https://docs.stripe.com/api/idempotent_requests)).
5. 처리 중인 키는 충돌로 거절한다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.7), [8](https://docs.stripe.com/error-codes), [24](https://docs.tosspayments.com/reference/using-api/idempotency-key)).
6. 키 기록과 본 처리를 하나의 ACID 작업으로 묶는다([29](https://builder.aws.com/content/3Ev0BENTyBr0XxzRk5FDZzgNYos/making-retries-safe-with-idempotent-apis)). 통째로 롤백된 작업이어야 통째로 다시 실행할 수 있다([6](https://stripe.com/blog/idempotency)).
7. 재시도해도 되는지, 재생된 응답인지 알려 준다(`Stripe-Should-Retry`, `Idempotent-Replayed`, `transient-error`)([5](https://docs.stripe.com/error-low-level#should-retry), [21](https://docs.adyen.com/development-resources/api-idempotency)).
8. 확정·취소·환불은 자원 상태로도 막는다([13](https://docs.stripe.com/api/payment_intents/cancel), [21](https://docs.adyen.com/development-resources/api-idempotency), [27](https://docs.tosspayments.com/reference/error-codes)).
9. 결과를 확인할 길을 준다(업무 ID 조회, 웹훅)([5](https://docs.stripe.com/error-low-level#server-errors), [26](https://docs.tosspayments.com/reference)).

### 클라이언트
1. 작업 하나에 키 하나를 만든다. UUID를 쓰거나 장바구니 ID 같은 업무 객체에서 끌어낸다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.2), [5](https://docs.stripe.com/error-low-level#idempotency)).
2. 키를 보내기 전에 보관하고, 화면 수명에 묶는다. 성공하면 새 키를 만든다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#storing-idempotency-keys)).
3. 결과를 모르면(타임아웃, 연결 끊김, 5xx, 처리 중 409) 같은 키·같은 본문으로 재시도한다([5](https://docs.stripe.com/error-low-level#network-errors), [17](https://developer.paypal.com/api/rest/reference/idempotency/), [24](https://docs.tosspayments.com/reference/using-api/idempotency-key), [38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#when-to-generate-a-new-key)).
4. 확정된 실패(4xx)는 요청을 고친 뒤 새 키로 보낸다([1](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html#section-2.7), [5](https://docs.stripe.com/error-low-level#content-errors), [31](https://docs.aws.amazon.com/ec2/latest/devguide/ec2-api-idempotency.html#recommended-actions)).
5. 재시도는 지수 백오프, 지터, 횟수 상한을 둔다([6](https://stripe.com/blog/idempotency), [30](https://builder.aws.com/content/3EumjoZascWd1oZiEgL8ORlv3qE/timeouts-retries-and-backoff-with-jitter)).
6. 같은 키 재시도는 서버 보관 기간 안에서만 믿는다([5](https://docs.stripe.com/error-low-level#idempotency), [17](https://developer.paypal.com/api/rest/reference/idempotency/)).
7. 제출 버튼 중복 클릭을 막고, 성공하면 결과 페이지로 보내 새로고침이 다시 제출하지 않게 한다([16](https://docs.stripe.com/payments/accept-a-payment-deferred), [44](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Redirections)).
8. 끝내 결과를 모르면 새로 만들기 전에 상태부터 조회한다([26](https://docs.tosspayments.com/reference), [38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency-errors)).
9. 같은 키·다른 내용 오류는 클라이언트 구현 버그로 보고 고친다([38](https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency#understanding-idempotency-errors)).

## 출처

모두 2026-10-06에 확인했다.

1. IETF, "The Idempotency-Key HTTP Header Field", draft-ietf-httpapi-idempotency-key-header-07 (2025-10-15). https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html
2. IETF Datatracker, "The Idempotency-Key HTTP Header Field" (문서 상태: -07, 2026-04-18 만료). https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
3. RFC 9110, "HTTP Semantics" (§9.2.2 Idempotent Methods, §15.4.4 303 See Other). https://www.rfc-editor.org/rfc/rfc9110.html
4. Stripe API Reference, "Idempotent requests". https://docs.stripe.com/api/idempotent_requests
5. Stripe Docs, "Advanced error handling". https://docs.stripe.com/error-low-level
6. Stripe Blog, "Designing robust and predictable APIs with idempotency" (Brandur Leach, 2017-02-22). https://stripe.com/blog/idempotency
7. Stripe API Reference, "Errors". https://docs.stripe.com/api/errors
8. Stripe Docs, "Error codes". https://docs.stripe.com/error-codes
9. Stripe Changelog, "Requests that reuse idempotency tokens but alter request parameters now throw an error" (2015-09-03). https://docs.stripe.com/changelog/2015-09-03/reuse-idempotency-tokens-alter-error
10. Stripe Docs, "API v2 overview" (Idempotency). https://docs.stripe.com/api-v2-overview
11. Stripe Docs, "The Payment Intents API". https://docs.stripe.com/payments/payment-intents
12. Stripe Docs, "How Payment Intents and Setup Intents work". https://docs.stripe.com/payments/paymentintents/lifecycle
13. Stripe API Reference, "Cancel a PaymentIntent". https://docs.stripe.com/api/payment_intents/cancel
14. Stripe API Reference, "Checkout Sessions". https://docs.stripe.com/api/checkout/sessions
15. Stripe Docs, "Fulfill orders". https://docs.stripe.com/checkout/fulfillment
16. Stripe Docs, "Collect payment details before creating an Intent". https://docs.stripe.com/payments/accept-a-payment-deferred
17. PayPal Developer, "Idempotency". https://developer.paypal.com/api/rest/reference/idempotency/
18. PayPal Developer, Orders v2 "Error Messages". https://developer.paypal.com/api/rest/reference/orders/v2/errors/
19. PayPal Developer, "API responses". https://developer.paypal.com/api/rest/responses/
20. PayPal Developer, "Orders API v2" 레퍼런스. https://developer.paypal.com/docs/api/orders/v2/
21. Adyen Docs, "API idempotency". https://docs.adyen.com/development-resources/api-idempotency
22. Square Developer, "Idempotency". https://developer.squareup.com/docs/build-basics/common-api-patterns/idempotency
23. Square Developer, "Using the REST API". https://developer.squareup.com/docs/build-basics/general-considerations/using-rest-api
24. 토스페이먼츠 개발자센터, "인증 및 기타 헤더 설정"(멱등키 헤더). https://docs.tosspayments.com/reference/using-api/idempotency-key
25. 토스페이먼츠 개발자센터 블로그, "멱등성이 뭔가요?" (2023-01-11). https://docs.tosspayments.com/blog/what-is-idempotency
26. 토스페이먼츠 개발자센터, "코어 API"(결제 승인, orderId로 결제 조회, 결제 취소). https://docs.tosspayments.com/reference
27. 토스페이먼츠 개발자센터, "API 에러 코드". https://docs.tosspayments.com/reference/error-codes
28. 토스페이먼츠 개발자센터, 결제위젯 "연동하기". https://docs.tosspayments.com/guides/v2/payment-widget/integration
29. Amazon Builders' Library, "Making retries safe with idempotent APIs" (현재 AWS Builder Center로 이전). https://builder.aws.com/content/3Ev0BENTyBr0XxzRk5FDZzgNYos/making-retries-safe-with-idempotent-apis
30. Amazon Builders' Library, "Timeouts, retries, and backoff with jitter" (Marc Brooker). https://builder.aws.com/content/3EumjoZascWd1oZiEgL8ORlv3qE/timeouts-retries-and-backoff-with-jitter (본문은 PDF판으로 확인: https://d1.awsstatic.com/builderslibrary/pdfs/timeouts-retries-and-backoff-with-jitter.pdf)
31. AWS Docs, "Ensuring idempotency in Amazon EC2 API requests". https://docs.aws.amazon.com/ec2/latest/devguide/ec2-api-idempotency.html
32. AWS Docs, "Exactly-once processing in Amazon SQS". https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-exactly-once-processing.html
33. Google AIP-155, "Request identification". https://google.aip.dev/155
34. Google AIP-194, "Automatic retry configuration". https://google.aip.dev/194
35. Google AIP-133, "Standard methods: Create". https://google.aip.dev/133
36. Google AIP-135, "Standard methods: Delete". https://google.aip.dev/135
37. Shopify Dev, "Idempotent requests". https://shopify.dev/docs/api/usage/idempotent-requests
38. Shopify Dev, "Implementing idempotency" (GraphQL Admin API). https://shopify.dev/docs/apps/build/apis/graphql-admin/implementing-idempotency
39. Shopify Dev, "Idempotent requests in the REST Admin API" (레거시, `unique_token`). https://shopify.dev/docs/api/admin-rest/usage/idempotent-requests
40. Shopify Dev, Payments Apps "Implementation considerations". https://shopify.dev/docs/apps/build/payments/considerations
41. Shopify Dev, "Payments app request reference". https://shopify.dev/docs/apps/build/payments/request-reference
42. Shopify Dev, "Cart" (Storefront API). https://shopify.dev/docs/storefronts/headless/building-with-the-storefront-api/cart
43. Shopify Dev, "Create and update a cart with the Storefront API". https://shopify.dev/docs/storefronts/headless/building-with-the-storefront-api/cart/manage
44. MDN Web Docs, "Redirections in HTTP". https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Redirections
