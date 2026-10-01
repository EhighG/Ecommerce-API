package com.ecommerce.api.product.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.common.openapi.ApiErrorCodes;
import com.ecommerce.api.product.dto.*;
import com.ecommerce.api.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "Product", description = "상품, 카테고리, 재고")
@RequiredArgsConstructor
@RequestMapping("/products")
@RestController
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록", description = "응답은 상품 ID다.")
    @ApiErrorCode(value = PRODUCT_CATEGORY_NOT_FOUND, when = "카테고리가 없음")
    @ApiErrorCode(value = UPLOADED_IMAGE_NOT_FOUND, when = "이미지가 없거나 남이 올린 이미지")
    @ApiErrorCode(value = IMAGE_ALREADY_ATTACHED, when = "이미 다른 상품에 붙은 이미지")
    @PostMapping
    public ResponseEntity<Long> register(@Valid @RequestBody RegisterProductReq req,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(productService.register(req, userDetails.getUserId()));
    }

    @Operation(summary = "상품 목록·검색")
    @ApiErrorCodes({})
    @GetMapping
    public ResponseEntity<ProductSearchRes> search(@ParameterObject @Valid @ModelAttribute SearchReq req) {
        Pageable pageable = PageRequest.of(
                req.page(),
                req.size()
        );
        return ResponseEntity
                .ok(productService.search(req, pageable));
    }

    @Operation(summary = "상품 상세", description = "조회할 때마다 조회수가 1 오른다.")
    @ApiErrorCode(value = PRODUCT_NOT_FOUND, when = "없거나 삭제된 상품")
    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailRes> getProductDetail(@PathVariable Long productId) {
        return ResponseEntity
                .ok(productService.getProductDetail(productId));
    }

    @Operation(summary = "상품 설명·단가 수정")
    @ApiErrorCode(value = SELLER_NOT_MATCHED, when = "내 상품이 아님")
    @ApiErrorCode(value = PRODUCT_NOT_FOUND, when = "없거나 삭제된 상품")
    @PatchMapping("/{productId}")
    public ResponseEntity<Void> modifyProduct(@PathVariable Long productId,
                                              @Valid @RequestBody ModifyProductReq req,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        productService.modifyProduct(productId, req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "상품 이미지 목록 교체")
    @ApiErrorCode(value = UPLOADED_IMAGE_NOT_FOUND, when = "이미지가 없거나 남이 올린 이미지")
    @ApiErrorCode(value = IMAGE_ALREADY_ATTACHED, when = "이미 다른 상품에 붙은 이미지")
    @ApiErrorCode(value = SELLER_NOT_MATCHED, when = "내 상품이 아님")
    @ApiErrorCode(value = PRODUCT_NOT_FOUND, when = "없거나 삭제된 상품")
    @PatchMapping("/{productId}/images")
    public ResponseEntity<Void> replaceProductImages(@PathVariable Long productId,
                                                     @Valid @RequestBody ReplaceProductImagesReq req,
                                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        productService.replaceProductImages(productId, req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "상품 삭제")
    @ApiErrorCode(value = SELLER_NOT_MATCHED, when = "내 상품이 아님")
    @ApiErrorCode(value = PRODUCT_NOT_FOUND, when = "없거나 삭제된 상품")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        productService.deleteProduct(productId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    /*
    카테고리
     */

    @Operation(summary = "카테고리 생성", description = "응답은 카테고리 ID다.")
    @ApiErrorCode(value = PRODUCT_CATEGORY_ALREADY_EXISTS, when = "같은 이름의 카테고리가 있음")
    @PostMapping("/category")
    public ResponseEntity<Long> addCategory(@Valid @RequestBody AddCategoryReq req) {
        return ResponseEntity
                .ok(productService.addCategory(req));
    }

    @Operation(summary = "카테고리 목록")
    @ApiErrorCodes({})
    @GetMapping("/category")
    public ResponseEntity<List<CategorySummary>> getCategories() {
        return ResponseEntity
                .ok(productService.getCategories());
    }

    @Operation(summary = "카테고리 삭제")
    @ApiErrorCode(value = PRODUCT_CATEGORY_NOT_FOUND, when = "카테고리가 없음")
    @ApiErrorCode(value = UNRECOGNIZED, when = "카테고리에 걸린 상품이 있음(삭제된 상품 포함)")
    @DeleteMapping("/category/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        productService.deleteCategory(categoryId);
        return ResponseEntity.ok().build();
    }
}
