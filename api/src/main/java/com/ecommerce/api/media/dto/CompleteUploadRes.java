package com.ecommerce.api.media.dto;

import com.ecommerce.api.media.entity.UploadedImage;

public record CompleteUploadRes(
        Long imageId,
        String objectKey,
        String imageUrl
) {
    public static CompleteUploadRes of(UploadedImage uploadedImage, String imageUrl) {
        return new CompleteUploadRes(
                uploadedImage.getId(),
                uploadedImage.getObjectKey(),
                imageUrl
        );
    }
}
