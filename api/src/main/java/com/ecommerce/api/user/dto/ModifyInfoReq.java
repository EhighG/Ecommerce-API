package com.ecommerce.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ModifyInfoReq(
        // all editable info
        @NotBlank(message = "닉네임은 필수입니다.") String nickname
) {}
