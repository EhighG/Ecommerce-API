package com.ecommerce.api.media.entity;

import com.ecommerce.api.common.util.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class UploadedImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String objectKey;

    @Column(nullable = false)
    private Long uploadUserId;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private boolean attached = false;

    @Builder
    public UploadedImage(String objectKey, Long uploadUserId, String contentType, long fileSize) {
        this.objectKey = objectKey;
        this.uploadUserId = uploadUserId;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public void markAttached() {
        this.attached = true;
    }
    public void detach() { this.attached = false; }
}
