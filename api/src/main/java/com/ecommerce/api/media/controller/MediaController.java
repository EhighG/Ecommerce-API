package com.ecommerce.api.media.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.common.openapi.ApiErrorCodes;
import com.ecommerce.api.common.openapi.OpenApiConfig;
import com.ecommerce.api.media.dto.*;
import com.ecommerce.api.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "Media", description = "이미지 업로드")
@RequiredArgsConstructor
@RequestMapping("/media")
@RestController
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "업로드 URL 발급", description = "응답의 `uploadUrl`로 GCS에 직접 `PUT`한 뒤 `POST /media/upload-complete`로 등록한다. "
            + "업로드 조건은 [business-rules.md](" + OpenApiConfig.DOCS_URL + "business-rules.md)의 이미지 업로드 절에 있다. "
            + "조건이 맞지 않거나 URL이 만료되면 GCS가 403을 준다.")
    @ApiErrorCode(value = INVALID_MEDIA_CONTENT_TYPE, when = "`contentType`이 이미지가 아님")
    @PostMapping("/upload-url")
    public ResponseEntity<CreateUploadUrlRes> createImageUploadUrl(
            @Valid @RequestBody CreateUploadUrlReq req
    ) {
        return ResponseEntity
                .ok(mediaService.createImageUploadUrl(req));
    }

    @Operation(summary = "업로드 완료 등록", description = "GCS에 올린 객체를 내 이미지로 등록한다. 응답의 `imageId`를 상품 등록과 이미지 교체에 쓴다.")
    @ApiErrorCode(value = MEDIA_OBJECT_NOT_FOUND, when = "GCS에 객체가 없음")
    @ApiErrorCode(value = INVALID_MEDIA_CONTENT_TYPE, when = "올린 객체가 이미지가 아님")
    @ApiErrorCode(value = IMAGE_OWNER_MISMATCH, when = "다른 사람이 이미 등록한 경로")
    @PostMapping("/upload-complete")
    public ResponseEntity<CompleteUploadRes> completeUpload(@Valid @RequestBody CompleteUploadReq req,
                                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(mediaService.completeUpload(req, userDetails.getUserId()));
    }

    @Operation(summary = "업로드 이미지 목록")
    @ApiErrorCodes({})
    @GetMapping("/uploaded-images")
    public ResponseEntity<List<UploadedImageListRes>> getUploadedImageInfoList() {
        return ResponseEntity
                .ok(mediaService.getUploadedImageInfoList());
    }
}
