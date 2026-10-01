package com.ecommerce.api.order.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.order.dto.OrderItemDetailRes;
import com.ecommerce.api.order.dto.OrderItemSearchReq;
import com.ecommerce.api.order.dto.OrderItemSearchRes;
import com.ecommerce.api.order.service.OrderItemService;
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

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "OrderItem", description = "주문항목 조회와 상태 변경")
@RequiredArgsConstructor
@RequestMapping("/order-items")
@RestController
public class OrderItemController {

    private final OrderItemService orderItemService;

    @Operation(summary = "주문항목 목록", description = "`orderId`와 `sellerId` 중 하나만 준다. 둘의 응답 형태가 다르다.")
    @ApiErrorCode(value = NO_PERMISSIONS, when = "조회 방식이 역할과 맞지 않음")
    @ApiErrorCode(value = ORDER_NOT_FOUND, when = "주문이 없거나 남의 주문")
    @ApiErrorCode(value = ORDER_ACCESS_DENIED, when = "`sellerId`가 본인이 아님")
    @GetMapping
    public ResponseEntity<OrderItemSearchRes> findOrderItems(@ParameterObject @Valid @ModelAttribute OrderItemSearchReq req,
                                                             @AuthenticationPrincipal CustomUserDetails userdetails) {
        Pageable pageable = PageRequest.of(req.page(), req.size());

        return ResponseEntity
                .ok(orderItemService.search(req, userdetails.getUserId(), userdetails.getUserRole(), pageable));
    }

    @Operation(summary = "주문항목 상세")
    @ApiErrorCode(value = ORDER_ITEM_NOT_FOUND, when = "주문항목이 없음")
    @ApiErrorCode(value = ORDER_ACCESS_DENIED, when = "구매자나 그 상품의 판매자가 아님")
    @GetMapping("/{orderItemId}")
    public ResponseEntity<OrderItemDetailRes> getOrderItemDetail(@PathVariable Long orderItemId,
                                                                 @AuthenticationPrincipal CustomUserDetails userdetails) {
        return ResponseEntity
                .ok(orderItemService.getOrderItemDetail(orderItemId, userdetails.getUserId()));
    }

    @Operation(summary = "배송 시작")
    @ApiErrorCode(value = WRONG_STATUS_CHANGE, when = "지금 상태에서 할 수 없는 변경")
    @ApiErrorCode(value = ORDER_ITEM_NOT_FOUND, when = "주문항목이 없음")
    @ApiErrorCode(value = ORDER_ACCESS_DENIED, when = "그 상품의 판매자가 아님")
    @ApiErrorCode(value = ORDER_STATUS_CONFLICT, when = "다른 요청이 먼저 상태를 바꿈. 새로고침한 뒤 다시 시도한다")
    @PatchMapping("/{orderItemId}/ship")
    public ResponseEntity<Void> ship(@PathVariable Long orderItemId,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderItemService.ship(orderItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "배송 완료")
    @ApiErrorCode(value = WRONG_STATUS_CHANGE, when = "지금 상태에서 할 수 없는 변경")
    @ApiErrorCode(value = ORDER_ITEM_NOT_FOUND, when = "주문항목이 없음")
    @ApiErrorCode(value = ORDER_ACCESS_DENIED, when = "그 상품의 판매자가 아님")
    @ApiErrorCode(value = ORDER_STATUS_CONFLICT, when = "다른 요청이 먼저 상태를 바꿈. 새로고침한 뒤 다시 시도한다")
    @PatchMapping("/{orderItemId}/deliver")
    public ResponseEntity<Void> deliver(@PathVariable Long orderItemId,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderItemService.deliver(orderItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "구매확정")
    @ApiErrorCode(value = WRONG_STATUS_CHANGE, when = "지금 상태에서 할 수 없는 변경")
    @ApiErrorCode(value = ORDER_ITEM_NOT_FOUND, when = "주문항목이 없음")
    @ApiErrorCode(value = ORDER_ACCESS_DENIED, when = "주문자가 아님")
    @ApiErrorCode(value = ORDER_STATUS_CONFLICT, when = "다른 요청이 먼저 상태를 바꿈. 새로고침한 뒤 다시 시도한다")
    @PatchMapping("/{orderItemId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long orderItemId,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderItemService.confirm(orderItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "주문항목 취소", description = "이미 취소된 항목이면 아무것도 바꾸지 않고 200을 준다.")
    @ApiErrorCode(value = WRONG_STATUS_CHANGE, when = "지금 상태에서 할 수 없는 변경")
    @ApiErrorCode(value = ORDER_ITEM_NOT_FOUND, when = "주문항목이 없음")
    @ApiErrorCode(value = ORDER_ACCESS_DENIED, when = "구매자나 그 상품의 판매자가 아님")
    @ApiErrorCode(value = ORDER_STATUS_CONFLICT, when = "다른 요청이 먼저 상태를 바꿈. 새로고침한 뒤 다시 시도한다")
    @PatchMapping("/{orderItemId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long orderItemId,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        orderItemService.cancel(orderItemId, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

}
