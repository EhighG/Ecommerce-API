package com.ecommerce.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ModifyPasswordReq(
        @NotBlank String oldPassword,
        @NotBlank String newPassword
) {}
