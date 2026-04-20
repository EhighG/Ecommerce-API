package com.ecommerce.api.inventory.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.inventory.dto.ModifyInventoryReq;
import com.ecommerce.api.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class InventoryController {

    private final InventoryService inventoryService;

    @PatchMapping("/products/inventory")
    public ResponseEntity<Void> modifyInventory(@Valid @RequestBody ModifyInventoryReq req,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        inventoryService.modifyInventory(req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }
}
