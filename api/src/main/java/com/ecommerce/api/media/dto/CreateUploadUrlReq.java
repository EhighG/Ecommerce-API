package com.ecommerce.api.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateUploadUrlReq(
        @Schema(description = "확장자만 쓴다") @NotBlank(message = "파일명은 필수입니다.") String originalFileName,
        @Schema(description = "`image/`로 시작해야 한다. GCS에 올릴 때 같은 값을 `Content-Type` 헤더로 보낸다", example = "image/png") @NotBlank(message = "파일 형식은 필수입니다.") String contentType
) {}
