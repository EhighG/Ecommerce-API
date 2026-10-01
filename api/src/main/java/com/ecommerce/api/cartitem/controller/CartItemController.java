package com.ecommerce.api.cartitem.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.cartitem.dto.AddItemReq;
import com.ecommerce.api.cartitem.dto.CartItemListRes;
import com.ecommerce.api.cartitem.dto.ChangeQuantityReq;
import com.ecommerce.api.cartitem.service.CartItemService;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.common.openapi.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "Cart", description = "장바구니")
@RequiredArgsConstructor
@RequestMapping("/cart-items")
@RestController
public class CartItemController {

    private final CartItemService cartItemService;

    @Operation(summary = "장바구니에 담기", description = "이미 담긴 상품이면 수량을 더한다. 응답은 장바구니 항목 ID다.")
    @ApiErrorCode(value = PRODUCT_NOT_FOUND, when = "없거나 삭제된 상품")
    @ApiErrorCode(value = CART_ITEM_CONFLICT, when = "같은 상품을 동시에 담아 충돌함. 다시 시도한다")
    @PostMapping
    public ResponseEntity<Long> addItem(@Valid @RequestBody AddItemReq req,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(cartItemService.addItem(req, userDetails.getUserId()));
    }

    @Operation(summary = "내 장바구니")
    @ApiErrorCodes({})
    @GetMapping
    public ResponseEntity<List<CartItemListRes>> findCartItems(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(cartItemService.findCartItems(userDetails.getUserId()));
    }

    @Operation(summary = "장바구니 수량 변경", description = "장바구니 항목 ID가 아니라 상품 ID로 항목을 찾는다.")
    @ApiErrorCode(value = CART_ITEM_NOT_FOUND, when = "그 상품이 장바구니에 없음")
    @PatchMapping("/quantity")
    public ResponseEntity<Void> changeQuantity(@Valid @RequestBody ChangeQuantityReq req,
                                               @AuthenticationPrincipal CustomUserDetails userDetails) {
        cartItemService.changeQuantity(req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "장바구니 항목 삭제")
    @ApiErrorCode(value = CART_ITEM_NOT_FOUND, when = "없거나 남의 항목")
    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long cartItemId,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        cartItemService.removeItem(cartItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }
}
