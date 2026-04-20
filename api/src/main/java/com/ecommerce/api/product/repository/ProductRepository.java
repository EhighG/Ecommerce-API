package com.ecommerce.api.product.repository;

import com.ecommerce.api.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductQueryRepository {
    Optional<Product> findByIdAndDeletedFalse(Long productId);
    List<Product> findAllBySellerIdAndDeletedFalse(Long sellerId);
    boolean existsByIdAndDeletedFalse(Long productId);

    @Modifying
    @Query("""
            update Product p
            set p.viewCount = p.viewCount + :delta
            where p.id = :productId
            """)
    void increaseViewCount(Long productId, long delta);
}
