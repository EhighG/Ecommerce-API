package com.ecommerce.api.product.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.media.entity.UploadedImage;
import com.ecommerce.api.media.service.MediaService;
import com.ecommerce.api.product.dto.*;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.entity.ProductCategory;
import com.ecommerce.api.product.entity.ProductImage;
import com.ecommerce.api.product.entity.ProductStat;
import com.ecommerce.api.product.repository.ProductCategoryRepository;
import com.ecommerce.api.product.repository.ProductImageRepository;
import com.ecommerce.api.product.repository.ProductRepository;
import com.ecommerce.api.product.repository.ProductStatRepository;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductStatRepository productStatRepository;
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

        productStatRepository.save(new ProductStat(product));

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
        if (imageInfoList.isEmpty()) return;

        List<Long> imageIds = imageInfoList.stream()
                .map(ImageIdWithOrder::imageId)
                .toList();

        List<UploadedImage> uploadedImages = mediaService.getUploadedImages(imageIds);
        if (uploadedImages.size() != imageIds.size()) {
            throw new AppException(UPLOADED_IMAGE_NOT_FOUND);
        }

        Map<Long, UploadedImage> uploadedImageMap = uploadedImages.stream()
                .collect(Collectors.toMap(UploadedImage::getId, ui -> ui));

        for (ImageIdWithOrder imageInfo : imageInfoList) {
            UploadedImage uploadedImage = uploadedImageMap.get(imageInfo.imageId());

            if (!uploadedImage.getUploadUserId().equals(sellerId))
                throw new AppException(IMAGE_OWNER_MISMATCH);
            if (uploadedImage.isAttached())
                throw new AppException(IMAGE_ALREADY_ATTACHED);
        }

        List<ProductImage> productImages = imageInfoList.stream()
                .map(info -> new ProductImage(
                        product.getId(),
                        info.order(),
                        uploadedImageMap.get(info.imageId())
                ))
                .toList();

        productImageRepository.saveAll(productImages);

        productImages.stream()
                .filter(pi -> pi.getDisplayOrder() == 1)
                .findFirst()
                .ifPresent(product::setThumbnailImage);
    }

    public void checkSellerMatched(Product product, Long sellerId) {
        if (!product.getSeller().getId().equals(sellerId))
            throw new AppException(SELLER_NOT_MATCHED);
    }

    private void validateProductImageInputs(List<ImageIdWithOrder> imageIdList) {
        if (imageIdList == null)
            throw new AppException(INVALID_INPUT);
    }

    private User getUserNotDeleted(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND));
    }

    /*
    카테고리
     */

    @Transactional
    public Long addCategory(AddCategoryReq req) {
        if (productCategoryRepository.existsByName(req.name())) {
            throw new AppException(PRODUCT_CATEGORY_ALREADY_EXISTS);
        }

        ProductCategory saved = productCategoryRepository.save(
                new ProductCategory(req.name())
        );

        return saved.getId();
    }

    public List<CategorySummary> getCategories() {
        return productCategoryRepository.findAllByOrderByIdAsc().stream()
                .map(CategorySummary::new)
                .toList();
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        ProductCategory category = productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(PRODUCT_CATEGORY_NOT_FOUND));

        productCategoryRepository.delete(category);
    }
}
