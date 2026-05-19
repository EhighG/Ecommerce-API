package com.ecommerce.api.auth.filter;

import com.ecommerce.api.auth.config.LoadTestAuthProperties;
import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.service.UserService;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.loadtest.auth.enabled",
        havingValue = "true"
)
@Profile("loadtest")
@Component
public class LoadTestAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-LoadTest-User-Id";
    private static final String SECRET_HEADER = "X-LoadTest-Secret";

    private final LoadTestAuthProperties properties;
    private final UserService userService;

    public static boolean isTestRequest(HttpServletRequest request) {
        return request.getHeader(USER_ID_HEADER) != null;
    }

    @PostConstruct
    void validate() {
        if (properties.enabled() && StringUtils.isBlank(properties.secret())) {
            throw new IllegalStateException("LoadTest Auth 사용 시 LOADTEST_AUTH_SECRET 필수");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || request.getHeader(USER_ID_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String secretHeaderValue = request.getHeader(SECRET_HEADER);

        // secret 헤더 값 기반으로 검증
        if (StringUtils.isBlank(secretHeaderValue) || !secretHeaderValue.equals(properties.secret())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "secret 불일치");
            return;
        }

        Long userId;
        try {
            userId = Long.valueOf(request.getHeader(USER_ID_HEADER));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "userId헤더 파싱 실패");
            return;
        }

        User user;
        try {
            user = userService.getUserNotDeleted(userId);
        } catch (AppException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return;
        }

        CustomUserDetails principal = CustomUserDetails.from(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
