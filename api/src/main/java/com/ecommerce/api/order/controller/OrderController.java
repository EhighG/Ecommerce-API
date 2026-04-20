package com.ecommerce.api.order.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.order.dto.OrderDetailRes;
import com.ecommerce.api.order.dto.OrderListRes;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/orders")
@RestController
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Long> order(@Valid @RequestBody OrderReq req,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(orderService.order(req, userDetails.getUserId()));
    }

    @GetMapping("/me")
    public ResponseEntity<List<OrderListRes>> findMyOrders(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(orderService.findByBuyerId(userDetails.getUserId()));
    }

    // 다른 사용자 주문목록 조회는 보류

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailRes> getMyOrderDetail(@PathVariable Long orderId,
                                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(orderService.getMyOrderDetail(orderId, userDetails.getUserId()));
    }
}
