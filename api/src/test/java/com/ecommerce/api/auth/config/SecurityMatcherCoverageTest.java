package com.ecommerce.api.auth.config;

import com.ecommerce.api.support.WebLayerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.ecommerce.api.auth.config.SecurityConfig.adminMatchers;
import static com.ecommerce.api.auth.config.SecurityConfig.allMatchers;
import static com.ecommerce.api.auth.config.SecurityConfig.buyerMatchers;
import static com.ecommerce.api.auth.config.SecurityConfig.sellerMatchers;
import static com.ecommerce.api.auth.config.SecurityConfig.userMatchers;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityMatcherCoverageTest extends WebLayerTestSupport {

    // 컨트롤러가 아니라 보안 필터가 처리하는 경로
    private static final List<String> FILTER_ENDPOINTS = List.of("POST /auth/login", "POST /auth/logout");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("모든 엔드포인트가 역할별 matcher 목록 중 하나에 들어 있다")
    void everyEndpoint_isListedInRoleMatchers() {
        List<RequestMatcher> matchers = Stream.of(adminMatchers(), allMatchers(), buyerMatchers(), sellerMatchers(), userMatchers())
                .flatMap(Arrays::stream)
                .toList();

        List<String> endpoints = new ArrayList<>(FILTER_ENDPOINTS);
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.ecommerce.api")) {
                return;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                // 메서드를 정하지 않은 매핑은 모든 메서드를 받는다
                Stream<RequestMethod> accepted = methods.isEmpty() ? Arrays.stream(RequestMethod.values()) : methods.stream();
                accepted.forEach(method -> endpoints.add(method.name() + " " + pattern));
            }
        });

        List<String> unlisted = endpoints.stream()
                .filter(endpoint -> matchers.stream().noneMatch(matcher -> matcher.matches(request(endpoint))))
                .sorted()
                .toList();

        assertThat(unlisted)
                .as("SecurityConfig의 역할별 matcher 목록에 넣는다. 넣지 않으면 로그인한 사용자 누구나 호출할 수 있다")
                .isEmpty();
    }

    private static MockHttpServletRequest request(String endpoint) {
        String[] methodAndPattern = endpoint.split(" ", 2);
        // 경로 변수 자리에는 아무 값이나 넣는다
        String path = methodAndPattern[1].replaceAll("\\{[^}]+}", "1");
        return new MockHttpServletRequest(methodAndPattern[0], path);
    }
}
