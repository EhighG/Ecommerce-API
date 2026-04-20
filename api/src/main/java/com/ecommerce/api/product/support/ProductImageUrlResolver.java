package com.ecommerce.api.product.support;

import com.ecommerce.api.media.service.MediaService;
import com.ecommerce.api.order.vo.ProductSnapshot;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.entity.ProductImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductImageUrlResolver {

    private final MediaService mediaService;

    public String resolveThumbnail(Product product) {
        if (product.getThumbnailImage() == null)
            return null;
        return resolve(product.getThumbnailImage());
    }

    public String resolveThumbnail(ProductSnapshot productSnapshot) {
        if (productSnapshot.getThumbnailPath() == null)
            return null;
        return mediaService.resolveUrl(productSnapshot.getThumbnailPath());
    }

    public List<String> resolveProductImages(List<ProductImage> productImageList) {
        return productImageList.stream()
                .map(this::resolve)
                .toList();
    }

    public String resolve(ProductImage productImage) {
        return mediaService.resolveUrl(
                productImage.getUploadedImage().getObjectKey()
        );
    }
}
