package com.ecommerce.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ModifyInfoReq(
        // all editable info
        @NotBlank String nickname
) {}
