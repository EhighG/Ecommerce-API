package com.ecommerce.api.inventory.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.inventory.dto.ModifyInventoryReq;
import com.ecommerce.api.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "Product")
@RequiredArgsConstructor
@RestController
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "재고 수정", description = "재고를 바뀐 뒤의 수량으로 바꾼다.")
    @ApiErrorCode(value = DELETED_PRODUCT, when = "삭제된 상품")
    @ApiErrorCode(value = SELLER_NOT_MATCHED, when = "내 상품이 아님")
    @PatchMapping("/products/inventory")
    public ResponseEntity<Void> modifyInventory(@Valid @RequestBody ModifyInventoryReq req,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        inventoryService.modifyInventory(req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }
}
