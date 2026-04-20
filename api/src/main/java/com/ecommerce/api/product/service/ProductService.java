package com.ecommerce.api.product.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.media.entity.UploadedImage;
import com.ecommerce.api.media.service.MediaService;
import com.ecommerce.api.product.dto.*;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.entity.ProductCategory;
import com.ecommerce.api.product.entity.ProductImage;
import com.ecommerce.api.product.repository.ProductCategoryRepository;
import com.ecommerce.api.product.repository.ProductImageRepository;
import com.ecommerce.api.product.repository.ProductRepository;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final UserRepository userRepository;
    private final MediaService mediaService;
    private final ProductDeletionService productDeletionService;
    private final InventoryService inventoryService;
    private final ProductViewCountService productViewCountService;

    @Transactional
    public Long register(RegisterProductReq req, Long userId) {
        validateProductImageInputs(req.imageIdList());

        User seller = getUserNotDeleted(userId);
        ProductCategory category = productCategoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new AppException(PRODUCT_CATEGORY_NOT_FOUND));

        Product product = productRepository.save(
                Product.register(
                        req.name(),
                        category,
                        req.description(),
                        req.unitPrice(),
                        seller
                )
        );

        inventoryService.createInventory(product, req.initialInventory());

        if (!req.imageIdList().isEmpty())
            attachImagesToProduct(req.imageIdList(), product, userId);

        return product.getId();
    }

    public ProductSearchRes search(SearchReq req, Pageable pageable) {
        Page<ProductListDto> page = productRepository.search(req, pageable);
        List<ProductListRes> content = page.getContent().stream()
                .map(dto -> {
                    String thumbnailUrl = null;
                    if (dto.thumbnailObjectKey() != null)
                        thumbnailUrl = mediaService.resolveUrl(dto.thumbnailObjectKey());

                    return ProductListRes.forProductList(dto, thumbnailUrl);
                }).toList();
        return new ProductSearchRes(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }

    public ProductDetailRes getProductDetail(Long productId) {
        ProductDetailDto product = productRepository.findProductDetail(productId)
                        .orElseThrow(() -> new AppException(PRODUCT_NOT_FOUND));

        productViewCountService.increase(productId);

        List<String> imageUrlList = productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(product.id()).stream()
                .map(productImage -> mediaService.resolveUrl(productImage.getUploadedImage()
                        .getObjectKey()))
                .toList();

        return ProductDetailRes.of(product, imageUrlList);
    }

    public Product getProduct(Long productId) {
        return productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new AppException(PRODUCT_NOT_FOUND));
    }

    public boolean existsNotDeleted(Long productId) {
        return productRepository.existsByIdAndDeletedFalse(productId);
    }

    @Transactional
    public void modifyProduct(Long productId, ModifyProductReq req, Long userId) {
        Product product = getProduct(productId);
        checkSellerMatched(product, userId);

        String description = req.description();
        if (description != null)
            product.setDescription(description);

        if (req.unitPrice() != null)
            product.setUnitPrice(req.unitPrice());
    }

    @Transactional
    public void replaceProductImages(Long productId, ReplaceProductImagesReq req, Long userId) {
        validateProductImageInputs(req.imageIdList());

        Product product = getProduct(productId);
        checkSellerMatched(product, userId);

        productDeletionService.removeProductImages(product);

        attachImagesToProduct(req.imageIdList(), product, userId);
    }

    @Transactional
    public void deleteProduct(Long productId, Long userId) {
        Product product = getProduct(productId);
        checkSellerMatched(product, userId);

        productDeletionService.delete(product);
    }

    private void attachImagesToProduct(List<ImageIdWithOrder> imageInfoList, Product product, Long sellerId) {
        for (ImageIdWithOrder imageInfo : imageInfoList) {
            UploadedImage uploadedImage = mediaService.getUploadedImage(imageInfo.imageId());

            if (!uploadedImage.getUploadUserId().equals(sellerId))
                throw new AppException(IMAGE_OWNER_MISMATCH);
            if (uploadedImage.isAttached())
                throw new AppException(IMAGE_ALREADY_ATTACHED);

            ProductImage productImage = productImageRepository.save(
                    new ProductImage(product.getId(), imageInfo.order(), uploadedImage)
            );
            if (imageInfo.order() == 1)
                product.setThumbnailImage(productImage);
        }
    }

    public void checkSellerMatched(Product product, Long sellerId) {
        if (!product.getSeller().getId().equals(sellerId))
            throw new AppException(SELLER_NOT_MATCHED);
    }

    private void removeProductImages(Product product, Long userId) {
        checkSellerMatched(product, userId);

        List<Long> uploadedImageIdList = productImageRepository.findUploadedImageIdsByProductId(product.getId());

        product.setThumbnailImage(null);

        if (!uploadedImageIdList.isEmpty()) {
            productImageRepository.deleteAllByProductId(product.getId());
            mediaService.detachAllById(uploadedImageIdList);
        }
    }

    private void validateProductImageInputs(List<ImageIdWithOrder> imageIdList) {
        if (imageIdList == null)
            throw new AppException(INVALID_INPUT);
    }

    private User getUserNotDeleted(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND));
    }
}
