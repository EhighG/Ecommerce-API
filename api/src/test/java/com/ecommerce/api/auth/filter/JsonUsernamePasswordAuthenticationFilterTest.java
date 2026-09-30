package com.ecommerce.api.auth.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonUsernamePasswordAuthenticationFilterTest {

    private final JsonUsernamePasswordAuthenticationFilter filter =
            new JsonUsernamePasswordAuthenticationFilter(JsonMapper.builder().build());

    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "{}",
            "{\"email\":\"user@e.com\"}",
            "not-json"
    })
    @DisplayName("본문이 JSON null이거나 비었거나 읽을 수 없으면 인증 실패(401)로 처리한다")
    void attemptAuthentication_withUnusableBody_throwsAuthenticationException(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        // AuthenticationException이면 실패 핸들러가 401로 응답한다. 그 밖의 예외는 500이 된다
        assertThatThrownBy(() -> filter.attemptAuthentication(request, new MockHttpServletResponse()))
                .isInstanceOf(AuthenticationException.class);
    }
}
