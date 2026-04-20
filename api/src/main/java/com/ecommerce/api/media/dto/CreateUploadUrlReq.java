package com.ecommerce.api.media.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUploadUrlReq(
        @NotBlank String originalFileName,
        @NotBlank String contentType
) {}
