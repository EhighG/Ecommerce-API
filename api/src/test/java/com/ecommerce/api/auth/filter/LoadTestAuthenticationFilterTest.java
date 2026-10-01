package com.ecommerce.api.auth.filter;

import com.ecommerce.api.auth.config.LoadTestAuthProperties;
import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static com.ecommerce.api.support.UnitTestFixtures.buyer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoadTestAuthenticationFilterTest {

    private static final String SECRET = "test-only-secret";

    private final UserService userService = mock(UserService.class);
    private final LoadTestAuthenticationFilter filter =
            new LoadTestAuthenticationFilter(new LoadTestAuthProperties(true, SECRET), userService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("세션으로 이미 인증된 요청이어도 부하테스트 헤더가 있으면 비밀값이 틀릴 때 401이다")
    void doFilter_withSessionAuthenticationAndWrongSecret_rejects() throws Exception {
        Authentication sessionAuthentication = sessionAuthentication();
        SecurityContextHolder.getContext().setAuthentication(sessionAuthentication);
        MockHttpServletRequest request = loadTestRequest("wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("세션으로 인증된 요청이 올바른 비밀값을 보내면 세션 사용자로 계속 진행한다")
    void doFilter_withSessionAuthenticationAndCorrectSecret_keepsSessionUser() throws Exception {
        Authentication sessionAuthentication = sessionAuthentication();
        SecurityContextHolder.getContext().setAuthentication(sessionAuthentication);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(loadTestRequest(SECRET), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(sessionAuthentication);
    }

    @Test
    @DisplayName("인증되지 않은 요청이 올바른 비밀값을 보내면 헤더의 사용자로 인증한다")
    void doFilter_withoutAuthenticationAndCorrectSecret_authenticatesHeaderUser() throws Exception {
        User user = buyer();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(userService.getUserNotDeleted(7L)).thenReturn(user);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(loadTestRequest(SECRET), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        CustomUserDetails principal =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(7L);
    }

    private MockHttpServletRequest loadTestRequest(String secret) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.addHeader("X-LoadTest-User-Id", "7");
        request.addHeader("X-LoadTest-Secret", secret);
        return request;
    }

    private Authentication sessionAuthentication() {
        User user = buyer();
        ReflectionTestUtils.setField(user, "id", 1L);
        CustomUserDetails principal = CustomUserDetails.from(user);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
