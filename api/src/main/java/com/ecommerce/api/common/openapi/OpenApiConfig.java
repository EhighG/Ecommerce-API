package com.ecommerce.api.common.openapi;

import com.ecommerce.api.common.api.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * API 명세(OpenAPI) 공통 설정. springdoc을 켠 프로필(local)과 명세 생성 테스트에서만 등록한다.
 * 생성된 명세는 docs/api/openapi.yaml에 커밋하고, OpenApiSpecTest가 코드와 같은지 검사한다.
 */
@Configuration
@ConditionalOnBooleanProperty("springdoc.api-docs.enabled")
public class OpenApiConfig {

    public static final String CSRF_SCHEME = "csrf";
    public static final String DOCS_URL = "https://github.com/EhighG/Ecommerce-API/blob/develop/docs/";

    private static final String JSON = "application/json";
    private static final String API_ERROR_REF = "#/components/schemas/ApiError";
    private static final Set<RequestMethod> STATE_CHANGING_METHODS =
            EnumSet.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    private static final String DESCRIPTION = """
            오픈마켓형 이커머스 REST API. 이 명세는 컨트롤러와 DTO에서 생성된다.

            - 세션·CSRF 절차, 주문 생성의 멱등 키, 오류 응답 형식과 공통 오류 매핑: [contracts.md](%1$scontracts.md)
            - 역할별 권한과 소유자 검사: [security.md](%1$ssecurity.md)의 "인가 규칙"
            - 엔드포인트마다 적은 오류는 클라이언트가 분기할 업무 오류다. 오류 응답의 `message`는 예시이고, 같은 `code`라도 더 구체적인 문장이 올 수 있다. 분기는 HTTP 상태와 `code`로만 한다.

            **Try it out(로컬 서버)**
            1. `GET /auth/csrf`를 실행하고 응답의 `token`을 복사한다.
            2. Authorize에서 `csrf`(`X-CSRF-TOKEN` 헤더)에 그 값을 넣는다.
            3. `POST /auth/login`으로 로그인한다. 세션 쿠키는 브라우저가 보낸다.

            로그아웃하거나 세션이 만료되면 1부터 다시 한다.
            """.formatted(DOCS_URL);

    @Bean
    OpenAPI openAPI() {
        Components components = new Components()
                .addSecuritySchemes(CSRF_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-CSRF-TOKEN")
                        .description("`GET /auth/csrf` 응답의 `token`. POST, PATCH, DELETE 요청에 필요하다."));
        ModelConverters.getInstance(true).read(ApiError.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("Ecommerce API")
                        .version("v1")
                        .description(DESCRIPTION))
                .servers(List.of(new Server().url("http://localhost:8081/api").description("로컬 서버")))
                .components(components);
    }

    @Bean
    OperationCustomizer apiOperationCustomizer() {
        return (operation, handlerMethod) -> {
            // 생성 순서와 무관하게 같은 명세가 나오도록 operationId를 직접 정한다
            operation.setOperationId(operationId(handlerMethod));
            if (changesState(handlerMethod.getMethod())) {
                operation.addSecurityItem(new SecurityRequirement().addList(CSRF_SCHEME));
            }
            addErrorResponses(operation, handlerMethod.getMethod());
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer sortTagsCustomizer() {
        return openApi -> {
            if (openApi.getTags() != null) {
                openApi.getTags().sort(Comparator.comparing(Tag::getName));
            }
        };
    }

    private static String operationId(HandlerMethod handlerMethod) {
        String controller = handlerMethod.getBeanType().getSimpleName().replace("Controller", "");
        return StringUtils.uncapitalize(controller) + "_" + handlerMethod.getMethod().getName();
    }

    private static boolean changesState(Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        return mapping != null && Arrays.stream(mapping.method()).anyMatch(STATE_CHANGING_METHODS::contains);
    }

    private static void addErrorResponses(Operation operation, Method method) {
        Map<Integer, List<ApiErrorCode>> byStatus = Arrays.stream(method.getAnnotationsByType(ApiErrorCode.class))
                .collect(groupingBy(error -> error.value().httpStatus().value(), TreeMap::new, toList()));

        byStatus.forEach((status, errors) -> {
            Map<String, Example> examples = new LinkedHashMap<>();
            errors.forEach(error -> examples.putIfAbsent(error.value().code(), example(error)));

            operation.getResponses().addApiResponse(String.valueOf(status), new ApiResponse()
                    .description(errors.stream().map(OpenApiConfig::describe).collect(joining("\n")))
                    .content(new Content().addMediaType(JSON, new MediaType()
                            .schema(new Schema<>().$ref(API_ERROR_REF))
                            .examples(examples))));
        });
    }

    private static String describe(ApiErrorCode error) {
        return "- `" + error.value().code() + "`: " + condition(error);
    }

    private static Example example(ApiErrorCode error) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", error.value().code());
        body.put("message", error.value().message());
        return new Example().summary(error.value().code() + " " + condition(error)).value(body);
    }

    private static String condition(ApiErrorCode error) {
        return error.when().isBlank() ? error.value().message() : error.when();
    }
}
