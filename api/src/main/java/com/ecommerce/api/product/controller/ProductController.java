package com.ecommerce.api.product.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.product.dto.*;
import com.ecommerce.api.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/products")
@RestController
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<Long> register(@Valid @RequestBody RegisterProductReq req,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(productService.register(req, userDetails.getUserId()));
    }

    @GetMapping
    public ResponseEntity<ProductSearchRes> search(@Valid @ModelAttribute SearchReq req) {
        Pageable pageable = PageRequest.of(
                req.page(),
                req.size()
        );
        return ResponseEntity
                .ok(productService.search(req, pageable));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailRes> getProductDetail(@PathVariable Long productId) {
        return ResponseEntity
                .ok(productService.getProductDetail(productId));
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<Void> modifyProduct(@PathVariable Long productId,
                                              @Valid @RequestBody ModifyProductReq req,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        productService.modifyProduct(productId, req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{productId}/images")
    public ResponseEntity<Void> replaceProductImages(@PathVariable Long productId,
                                                     @Valid @RequestBody ReplaceProductImagesReq req,
                                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        productService.replaceProductImages(productId, req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        productService.deleteProduct(productId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }
}
