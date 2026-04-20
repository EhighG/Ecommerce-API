package com.ecommerce.api.auth.dto;

import org.springframework.security.web.csrf.CsrfToken;

public record GetCsrfTokenRes(
        String headerName,
        String paramName,
        String token
) {
    public static GetCsrfTokenRes from(CsrfToken token) {
        return new GetCsrfTokenRes(
                token.getHeaderName(),
                token.getParameterName(),
                token.getToken()
        );
    }
}
