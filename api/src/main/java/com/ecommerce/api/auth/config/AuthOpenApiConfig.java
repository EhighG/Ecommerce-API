package com.ecommerce.api.auth.config;

import com.ecommerce.api.auth.dto.LoginReq;
import com.ecommerce.api.common.openapi.OpenApiConfig;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 로그인·로그아웃은 컨트롤러가 아니라 보안 필터({@link SecurityConfig})가 처리해서 springdoc이 찾지 못한다.
 * 그래서 두 경로를 명세에 직접 넣는다. 필터의 경로나 응답을 바꾸면 여기도 고친다.
 */
@Configuration
@ConditionalOnBooleanProperty("springdoc.api-docs.enabled")
public class AuthOpenApiConfig {

    private static final String TAG = "Auth";

    @Bean
    OpenApiCustomizer authEndpointsCustomizer() {
        return openApi -> {
            ModelConverters.getInstance(true).read(LoginReq.class)
                    .forEach(openApi.getComponents()::addSchemas);

            openApi.path("/auth/login", new PathItem().post(new Operation()
                    .tags(List.of(TAG))
                    .operationId("auth_login")
                    .summary("로그인")
                    .description("현재 세션을 인증한다.")
                    .requestBody(new RequestBody()
                            .required(true)
                            .content(new Content().addMediaType("application/json", new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/LoginReq")))))
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("로그인 성공. 본문 없음"))
                            .addApiResponse("401", new ApiResponse()
                                    .description("로그인 실패. 본문 없음. 없는 이메일, 틀린 비밀번호, 탈퇴한 사용자를 구분하지 않는다")))
                    .addSecurityItem(new SecurityRequirement().addList(OpenApiConfig.CSRF_SCHEME))));

            openApi.path("/auth/logout", new PathItem().post(new Operation()
                    .tags(List.of(TAG))
                    .operationId("auth_logout")
                    .summary("로그아웃")
                    .description("세션을 무효화한다.")
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("본문 없음")))
                    .addSecurityItem(new SecurityRequirement().addList(OpenApiConfig.CSRF_SCHEME))));
        };
    }
}
