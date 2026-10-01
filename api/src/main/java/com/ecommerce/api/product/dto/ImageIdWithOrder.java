package com.ecommerce.api.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ImageIdWithOrder(
        @Schema(description = "업로드 완료 등록 응답의 `imageId`") @NotNull(message = "이미지 ID는 필수입니다.") Long imageId,
        @Schema(description = "표시 순서. 1이 썸네일이다") @NotNull(message = "이미지 표시 순서는 필수입니다.") @Min(value = 1, message = "이미지 표시 순서는 1 이상이어야 합니다.") Integer order
) {}
