package com.ecommerce.api.order.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.common.openapi.ApiErrorCodes;
import com.ecommerce.api.common.openapi.OpenApiConfig;
import com.ecommerce.api.order.dto.OrderDetailRes;
import com.ecommerce.api.order.dto.OrderListRes;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.service.IdempotentOrderPlacementService;
import com.ecommerce.api.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "Order", description = "주문")
@RequiredArgsConstructor
@RequestMapping("/orders")
@RestController
public class OrderController {

    private final OrderService orderService;
    private final IdempotentOrderPlacementService idempotentOrderPlacementService;

    @Operation(summary = "주문 생성", description = "장바구니 항목으로 주문한다. 응답은 주문 ID다. "
            + "멱등 키를 만들고 재시도하는 방법은 [contracts.md](" + OpenApiConfig.DOCS_URL + "contracts.md)의 주문 생성의 멱등 키 절에 있다.")
    @ApiErrorCode(value = IDEMPOTENCY_KEY_REQUIRED, when = "`Idempotency-Key` 헤더가 없음")
    @ApiErrorCode(value = IDEMPOTENCY_KEY_INVALID, when = "`Idempotency-Key` 형식이 틀림")
    @ApiErrorCode(value = IDEMPOTENCY_KEY_CONFLICT, when = "같은 키로 다른 내용을 보냄. 같은 키로 다시 보내지 않는다")
    @ApiErrorCode(value = IDEMPOTENCY_REQUEST_PROCESSING, when = "같은 키의 요청이 처리 중이거나 막 처리됨. 잠시 후 같은 키로 다시 보낸다")
    @ApiErrorCode(value = INVALID_INPUT, when = "한 주문에 같은 장바구니 항목이 두 번 있음")
    @ApiErrorCode(value = INVALID_INPUT, when = "할인액이 0원이 되는 쿠폰을 씀. 메시지는 경우마다 다르다")
    @ApiErrorCode(value = CART_ITEM_NOT_FOUND, when = "장바구니 항목이 없거나 남의 항목. 상품을 삭제하면 장바구니 항목도 지워지므로 삭제된 상품도 대개 이 코드다")
    @ApiErrorCode(value = DELETED_PRODUCT, when = "상품 삭제와 주문이 동시에 일어남")
    @ApiErrorCode(value = DUPLICATED_COUPON, when = "같은 쿠폰을 두 번 씀")
    @ApiErrorCode(value = COUPON_ISSUED_NOT_FOUND, when = "쿠폰이 없거나 남의 쿠폰")
    @ApiErrorCode(value = COUPON_EXPIRED, when = "쓸 수 없는 쿠폰(이미 사용, 만료)")
    @ApiErrorCode(value = INSUFFICIENT_INVENTORY, when = "재고가 모자람")
    @PostMapping
    public ResponseEntity<Long> order(@Parameter(description = "규칙은 [contracts.md](" + OpenApiConfig.DOCS_URL + "contracts.md)의 주문 생성의 멱등 키 절에 있다.",
                                              required = true,
                                              schema = @Schema(pattern = "^[A-Za-z0-9._:-]{1,128}$", example = "3f1e9a2c-7b4d-4c1e-9f0a-2b6c8d1e5a70"))
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                      @Valid @RequestBody OrderReq req,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(idempotentOrderPlacementService.placeOrder(req, userDetails.getUserId(), idempotencyKey));
    }

    @Operation(summary = "내 주문 목록")
    @ApiErrorCodes({})
    @GetMapping("/me")
    public ResponseEntity<List<OrderListRes>> findMyOrders(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(orderService.findByBuyerId(userDetails.getUserId()));
    }

    // 다른 사용자 주문목록 조회는 보류

    @Operation(summary = "주문 상세")
    @ApiErrorCode(value = ORDER_NOT_FOUND, when = "주문이 없음")
    @ApiErrorCode(value = ORDER_ACCESS_DENIED, when = "남의 주문")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailRes> getMyOrderDetail(@PathVariable Long orderId,
                                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(orderService.getMyOrderDetail(orderId, userDetails.getUserId()));
    }
}
