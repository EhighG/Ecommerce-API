package com.ecommerce.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawReq(
        @NotBlank String password
) {}
