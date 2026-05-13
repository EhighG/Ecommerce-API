package com.ecommerce.api.auth.filter;

import com.ecommerce.api.auth.dto.LoginReq;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

public class JsonUsernamePasswordAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private final ObjectMapper objectMapper;

    public JsonUsernamePasswordAuthenticationFilter(ObjectMapper objectMapper) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/login"));
        this.objectMapper = objectMapper;
    }

    @Override
    public @Nullable Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        // Content-Type 제약 필요할지?
        LoginReq loginReq;

        try {
            loginReq = objectMapper.readValue(request.getInputStream(), LoginReq.class);
        } catch (IOException | JacksonException e) {
            throw new AuthenticationServiceException("로그인 요청 body 파싱 실패", e);
        }

        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                loginReq.email(), loginReq.password()
        );

        return this.getAuthenticationManager().authenticate(token);
    }
}
