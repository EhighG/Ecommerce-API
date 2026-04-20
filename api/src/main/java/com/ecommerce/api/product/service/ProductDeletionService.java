package com.ecommerce.api.product.service;

import com.ecommerce.api.cartitem.repository.CartItemRepository;
import com.ecommerce.api.media.service.MediaService;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.repository.ProductImageRepository;
import com.ecommerce.api.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Transactional
@Service
public class ProductDeletionService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final MediaService mediaService;
    private final CartItemRepository cartItemRepository;

    public void delete(Product product) {
        removeCartItems(product.getId());
        removeProductImages(product);
        productRepository.delete(product);
    }

    public void deleteAllBySeller(Long sellerId) {
        List<Product> productList = productRepository.findAllBySellerIdAndDeletedFalse(sellerId);
        for (Product product : productList) {
            delete(product);
        }
    }

    private void removeCartItems(Long productId) {
        cartItemRepository.deleteAllByProductId(productId);
    }

    public void removeProductImages(Product product) {
        List<Long> uploadedImageIdList = productImageRepository.findUploadedImageIdsByProductId(product.getId());

        product.setThumbnailImage(null);

        if (!uploadedImageIdList.isEmpty()) {
            productImageRepository.deleteAllByProductId(product.getId());
            mediaService.detachAllById(uploadedImageIdList);
        }
    }
}
