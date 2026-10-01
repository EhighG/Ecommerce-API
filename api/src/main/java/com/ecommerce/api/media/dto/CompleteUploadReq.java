package com.ecommerce.api.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CompleteUploadReq(
        @Schema(description = "업로드 URL 발급 응답의 `objectKey`") @NotBlank(message = "업로드 파일 Key는 필수입니다.") String objectKey
) {
}
