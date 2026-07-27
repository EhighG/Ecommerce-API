package com.ecommerce.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ModifyPasswordReq(
        @NotBlank(message = "현재 비밀번호는 필수입니다.") String oldPassword,
        @NotBlank(message = "새 비밀번호는 필수입니다.") String newPassword
) {}
