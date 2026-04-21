package com.ecommerce.api.media.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.media.dto.*;
import com.ecommerce.api.media.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/uploaded-images")
    public ResponseEntity<List<UploadedImageListRes>> getUploadedImages() {
        return ResponseEntity
                .ok(mediaService.getUploadedImages());
    }
}
