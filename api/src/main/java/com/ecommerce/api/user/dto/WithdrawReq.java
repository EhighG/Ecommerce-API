package com.ecommerce.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawReq(
        @NotBlank(message = "비밀번호는 필수입니다.") String password
) {}
