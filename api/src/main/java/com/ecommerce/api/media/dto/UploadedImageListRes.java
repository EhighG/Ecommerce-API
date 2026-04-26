package com.ecommerce.api.media.dto;

import com.ecommerce.api.media.entity.UploadedImage;

public record UploadedImageListRes(
        Long id,
        String objectKey,
        Long uploadUserId,
        String contentType,
        long fileSize,
        boolean attached
) {
    public UploadedImageListRes(UploadedImage uploadedImage) {
        this(
                uploadedImage.getId(),
                uploadedImage.getObjectKey(),
                uploadedImage.getUploadUserId(),
                uploadedImage.getContentType(),
                uploadedImage.getFileSize(),
                uploadedImage.isAttached()
        );
    }
}
