package com.ecommerce.api.review.repository;

import com.ecommerce.api.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByProductIdAndWriterId(Long productId, Long writerId);
    long countByWriterId(Long writerId);

    @EntityGraph(attributePaths = {
            "product",
            "product.thumbnailImage",
            "product.thumbnailImage.uploadedImage"
    })
    List<Review> findTop5ByWriterIdOrderByCreatedAtDesc(Long writerId);

    @EntityGraph(attributePaths = "writer")
    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "product",
            "product.thumbnailImage",
            "product.thumbnailImage.uploadedImage"
    })
    Page<Review> findByWriterIdOrderByCreatedAtDesc(Long writerId, Pageable pageable);
}
