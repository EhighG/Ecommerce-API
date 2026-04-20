package com.ecommerce.api.media.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.media.dto.CompleteUploadReq;
import com.ecommerce.api.media.dto.CompleteUploadRes;
import com.ecommerce.api.media.dto.CreateUploadUrlReq;
import com.ecommerce.api.media.dto.CreateUploadUrlRes;
import com.ecommerce.api.media.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/media")
@RestController
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload-url")
    public ResponseEntity<CreateUploadUrlRes> createImageUploadUrl(
            @Valid @RequestBody CreateUploadUrlReq req
    ) {
        return ResponseEntity
                .ok(mediaService.createImageUploadUrl(req));
    }

    @PostMapping("/upload-complete")
    public ResponseEntity<CompleteUploadRes> completeUpload(@Valid @RequestBody CompleteUploadReq req,
                                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(mediaService.completeUpload(req, userDetails.getUserId()));
    }
}
