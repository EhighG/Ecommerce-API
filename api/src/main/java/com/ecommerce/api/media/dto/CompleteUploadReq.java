package com.ecommerce.api.media.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteUploadReq(
        @NotBlank String objectKey
) {
}
