package com.ecommerce.api.product.repository;

import com.ecommerce.api.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("""
            select pi
            from ProductImage pi
            join fetch pi.uploadedImage ui
            where pi.productId = :productId
            order by pi.displayOrder asc
            """)
    List<ProductImage> findAllByProductIdOrderByDisplayOrderAsc(Long productId);

    @Query("""
            select pi.uploadedImage.id
            from ProductImage pi
            where pi.productId = :productId
            order by pi.displayOrder asc
            """)
    List<Long> findUploadedImageIdsByProductId(Long productId);

    @Modifying
    @Query("""
            delete from ProductImage pi
            where pi.productId = :productId
            """)
    void deleteAllByProductId(Long productId);
}
