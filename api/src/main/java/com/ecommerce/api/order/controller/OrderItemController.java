package com.ecommerce.api.order.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.order.dto.OrderItemDetailRes;
import com.ecommerce.api.order.dto.OrderItemListRes;
import com.ecommerce.api.order.service.OrderItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/order-items")
@RestController
public class OrderItemController {

    private final OrderItemService orderItemService;

    @GetMapping
    public ResponseEntity<List<OrderItemListRes>> findByOrder(@RequestParam Long orderId,
                                                              @AuthenticationPrincipal CustomUserDetails userdetails) {
        return ResponseEntity
                .ok(orderItemService.findByOrder(orderId, userdetails.getUserId()));
    }

    // TODO: 판매자 입장에서의 주문항목 조회도 필요함

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
