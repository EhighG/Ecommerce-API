package com.ecommerce.api.cartitem.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.cartitem.dto.AddItemReq;
import com.ecommerce.api.cartitem.dto.CartItemListRes;
import com.ecommerce.api.cartitem.dto.ChangeQuantityReq;
import com.ecommerce.api.cartitem.service.CartItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/cart-items")
@RestController
public class CartItemController {

    private final CartItemService cartItemService;

    @PostMapping
    public ResponseEntity<Long> addItem(@Valid @RequestBody AddItemReq req,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(cartItemService.addItem(req, userDetails.getUserId()));
    }

    @GetMapping
    public ResponseEntity<List<CartItemListRes>> findCartItems(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(cartItemService.findCartItems(userDetails.getUserId()));
    }

    @PatchMapping("/quantity")
    public ResponseEntity<Void> changeQuantity(@Valid @RequestBody ChangeQuantityReq req,
                                               @AuthenticationPrincipal CustomUserDetails userDetails) {
        cartItemService.changeQuantity(req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long cartItemId,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        cartItemService.removeItem(cartItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }
}
