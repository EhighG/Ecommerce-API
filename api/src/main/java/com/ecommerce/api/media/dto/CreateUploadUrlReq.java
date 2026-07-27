package com.ecommerce.api.media.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUploadUrlReq(
        @NotBlank(message = "파일명은 필수입니다.") String originalFileName,
        @NotBlank(message = "파일 형식은 필수입니다.") String contentType
) {}
