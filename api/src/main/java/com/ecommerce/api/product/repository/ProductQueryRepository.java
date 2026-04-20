package com.ecommerce.api.product.repository;

import com.ecommerce.api.product.dto.ProductDetailDto;
import com.ecommerce.api.product.dto.ProductListDto;
import com.ecommerce.api.product.dto.SearchReq;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductQueryRepository {
    Page<ProductListDto> search(SearchReq condition, Pageable pageable);
    Optional<ProductDetailDto> findProductDetail(Long productId);
}
