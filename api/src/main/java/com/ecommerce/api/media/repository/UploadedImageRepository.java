package com.ecommerce.api.media.repository;

import com.ecommerce.api.media.entity.UploadedImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, Long> {
    Optional<UploadedImage> findByObjectKey(String objectKey);

    @Modifying
    @Query("""
            update UploadedImage ui
            set ui.attached = false
            where ui.id in :imageIds
            """)
    void detachAllByIdIn(@Param("imageIds") List<Long> imageIds);

    List<UploadedImage> findAllByOrderByIdAsc();
}
