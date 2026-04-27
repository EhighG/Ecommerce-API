package com.ecommerce.api.product.repository;

import com.ecommerce.api.product.entity.ProductStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductStatRepository extends JpaRepository<ProductStat, Long> {

    @Modifying
    @Query("""
            update ProductStat ps
            set ps.orderItemCount = ps.orderItemCount + :delta
            where ps.productId in :productIds
            """)
    int increaseOrderItemCountIn(List<Long> productIds, long delta);

    @Modifying
    @Query("""
            update ProductStat ps
            set ps.orderItemCount = ps.orderItemCount + :delta
            where ps.productId = :productId
            """)
    int increaseOrderItemCount(Long productId, long delta);

    @Modifying
    @Query("""
            update ProductStat ps
            set ps.ratingAvg = (ps.ratingSum + :halfStars) / (ps.reviewCount + 1) / 2.0,
                ps.reviewCount = ps.reviewCount + 1,
                ps.ratingSum = ps.ratingSum + :halfStars
            where ps.productId = :productId
            """)
    int addReview(Long productId, int halfStars);

    @Modifying
    @Query("""
            update ProductStat ps
            set ps.ratingAvg = (ps.ratingSum + :deltaHalfStars) / ps.reviewCount / 2.0,
                ps.ratingSum = ps.ratingSum + :deltaHalfStars
            where ps.productId = :productId
            """)
    int changeReviewRating(Long productId, long deltaHalfStars);
}
