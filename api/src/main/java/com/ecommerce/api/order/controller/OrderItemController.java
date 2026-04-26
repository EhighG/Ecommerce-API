package com.ecommerce.api.order.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.order.dto.OrderItemDetailRes;
import com.ecommerce.api.order.dto.OrderItemSearchReq;
import com.ecommerce.api.order.dto.OrderItemSearchRes;
import com.ecommerce.api.order.service.OrderItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/order-items")
@RestController
public class OrderItemController {

    private final OrderItemService orderItemService;

    @GetMapping
    public ResponseEntity<OrderItemSearchRes> findOrderItems(@Valid @ModelAttribute OrderItemSearchReq req,
                                                             @AuthenticationPrincipal CustomUserDetails userdetails) {
        Pageable pageable = PageRequest.of(req.page(), req.size());

        return ResponseEntity
                .ok(orderItemService.search(req, userdetails.getUserId(), userdetails.getUserRole(), pageable));
    }

    @GetMapping("/{orderItemId}")
    public ResponseEntity<OrderItemDetailRes> getOrderItemDetail(@PathVariable Long orderItemId,
                                                                 @AuthenticationPrincipal CustomUserDetails userdetails) {
        return ResponseEntity
                .ok(orderItemService.getOrderItemDetail(orderItemId, userdetails.getUserId()));
    }

    @PatchMapping("/{orderItemId}/ship")
    public ResponseEntity<Void> ship(@PathVariable Long orderItemId,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderItemService.ship(orderItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{orderItemId}/deliver")
    public ResponseEntity<Void> deliver(@PathVariable Long orderItemId,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderItemService.deliver(orderItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{orderItemId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long orderItemId,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderItemService.confirm(orderItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{orderItemId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long orderItemId,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderItemService.cancel(orderItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

}
