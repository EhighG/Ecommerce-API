package com.ecommerce.api.auth.controller;

import com.ecommerce.api.auth.dto.GetCsrfTokenRes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/auth")
@RestController
public class AuthController {

    @GetMapping("/csrf")
    public ResponseEntity<GetCsrfTokenRes> getCsrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok(GetCsrfTokenRes.from(csrfToken));
    }
}
