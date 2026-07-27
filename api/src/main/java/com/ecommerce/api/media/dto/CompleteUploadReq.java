package com.ecommerce.api.media.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteUploadReq(
        @NotBlank(message = "업로드 파일 Key는 필수입니다.") String objectKey
) {
}
