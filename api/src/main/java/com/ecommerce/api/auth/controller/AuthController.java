package com.ecommerce.api.auth.controller;

import com.ecommerce.api.auth.dto.GetCsrfTokenRes;
import com.ecommerce.api.common.openapi.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "세션, CSRF 토큰, 로그인·로그아웃")
@RequiredArgsConstructor
@RequestMapping("/auth")
@RestController
public class AuthController {

    @Operation(summary = "세션 생성과 CSRF 토큰 발급", description = "세션이 없으면 만들고, 상태 변경 요청에 넣을 CSRF 헤더 이름과 토큰을 준다.")
    @ApiErrorCodes({})
    @GetMapping("/csrf")
    public ResponseEntity<GetCsrfTokenRes> getCsrfToken(@Parameter(hidden = true) CsrfToken csrfToken) {
        return ResponseEntity.ok(GetCsrfTokenRes.from(csrfToken));
    }
}
