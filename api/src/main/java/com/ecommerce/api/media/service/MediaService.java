package com.ecommerce.api.media.service;

import com.ecommerce.api.common.config.StorageProperties;
import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.media.dto.CompleteUploadReq;
import com.ecommerce.api.media.dto.CompleteUploadRes;
import com.ecommerce.api.media.dto.CreateUploadUrlReq;
import com.ecommerce.api.media.dto.CreateUploadUrlRes;
import com.ecommerce.api.media.entity.UploadedImage;
import com.ecommerce.api.media.repository.UploadedImageRepository;
import com.ecommerce.api.media.storage.ObjectStorageClient;
import com.ecommerce.api.media.storage.SignedUploadUrl;
import com.ecommerce.api.media.storage.StoredObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class MediaService {

    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(10);

    private final UploadedImageRepository uploadedImageRepository;
    private final ObjectStorageClient objectStorageClient;
    private final StorageProperties storageProperties;

    public CreateUploadUrlRes createImageUploadUrl(CreateUploadUrlReq req) {
        validateImageContentType(req.contentType());

        String objectKey = buildObjectKey(req.originalFileName());
        SignedUploadUrl uploadUrl = objectStorageClient.createSignedUploadUrl(
                objectKey,
                req.contentType(),
                UPLOAD_URL_TTL
        );

        return CreateUploadUrlRes.from(uploadUrl);
    }

    public CompleteUploadRes completeUpload(CompleteUploadReq req, Long uploadUserId) {
        UploadedImage existing = uploadedImageRepository.findByObjectKey(req.objectKey()).orElse(null);
        if (existing != null) {
            validateOwner(existing, uploadUserId);
            return CompleteUploadRes.of(existing, resolveUrl(existing.getObjectKey()));
        }

        StoredObject object = objectStorageClient.getObject(req.objectKey())
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_OBJECT_NOT_FOUND));

        validateImageContentType(object.contentType());

        UploadedImage saved = uploadedImageRepository.save(
                UploadedImage.builder()
                        .objectKey(object.objectKey())
                        .uploadUserId(uploadUserId)
                        .contentType(object.contentType())
                        .fileSize(object.fileSize())
                        .build()
        );

        return CompleteUploadRes.of(saved, resolveUrl(saved.getObjectKey()));
    }

    @Transactional(readOnly = true)
    public UploadedImage getUploadedImage(Long imageId) {
        return uploadedImageRepository.findById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.UPLOADED_IMAGE_NOT_FOUND));
    }

    public String resolveUrl(String objectKey) {
        if (objectKey == null)
            return null;

        return objectStorageClient.getPublicUrl(objectKey);
    }

    public void detachAllById(List<Long> uploadedImageIdList) {
        uploadedImageRepository.detachAllByIdIn(uploadedImageIdList);
    }

    private void validateOwner(UploadedImage uploadedImage, Long userId) {
        if (!uploadedImage.getUploadUserId().equals(userId))
            throw new AppException(ErrorCode.IMAGE_OWNER_MISMATCH);
    }

    private void validateImageContentType(String contentType) {
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/"))
            throw new AppException(ErrorCode.INVALID_MEDIA_CONTENT_TYPE);
    }

    private String buildObjectKey(String originFileName) {
        String prefix = storageProperties.gcs().uploadPrefix();
        String datePath = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String extension = extractExtension(originFileName);
        String fileName = UUID.randomUUID() + extension;

        if (!StringUtils.hasText(prefix))
            return datePath + "/" + fileName;
        return prefix + "/" + datePath + "/" + fileName;
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName))
            return "";

        int index = fileName.lastIndexOf('.');
        if (index == -1)
            return "";
        return fileName.substring(index).toLowerCase();
    }
}
