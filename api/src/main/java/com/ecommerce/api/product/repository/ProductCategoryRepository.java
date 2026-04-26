package com.ecommerce.api.product.repository;

import com.ecommerce.api.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    boolean existsByName(String name);
    List<ProductCategory> findAllByOrderByIdAsc();
}
