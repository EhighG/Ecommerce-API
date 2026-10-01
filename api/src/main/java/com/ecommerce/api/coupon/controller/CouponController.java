package com.ecommerce.api.coupon.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.common.openapi.ApiErrorCodes;
import com.ecommerce.api.coupon.dto.CouponEventDetailRes;
import com.ecommerce.api.coupon.dto.CreateCouponEventReq;
import com.ecommerce.api.coupon.dto.IssueCouponRes;
import com.ecommerce.api.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "Coupon", description = "쿠폰 이벤트와 발급")
@RequiredArgsConstructor
@RequestMapping("/coupons")
@RestController
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "쿠폰 이벤트 조회")
    @ApiErrorCode(value = COUPON_EVENT_NOT_FOUND, when = "이벤트가 없음")
    @GetMapping("/events/{couponEventId}")
    public ResponseEntity<CouponEventDetailRes> getCouponEventDetail(@PathVariable Long couponEventId) {
        return ResponseEntity
                .ok(couponService.getCouponEventDetail(couponEventId));
    }

    @Operation(summary = "쿠폰 발급(선착순)", description = "응답의 `couponIssuedId`를 주문할 때 쓴다.")
    @ApiErrorCode(value = COUPON_EVENT_CLOSED, when = "발급 기간이 아님")
    @ApiErrorCode(value = COUPON_ALREADY_ISSUED, when = "이미 이 이벤트의 쿠폰을 받음")
    @ApiErrorCode(value = COUPON_SOLD_OUT, when = "수량이 모두 소진됨")
    @PostMapping("/events/{couponEventId}/issue")
    public ResponseEntity<IssueCouponRes> issueCoupon(@PathVariable Long couponEventId,
                                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(couponService.issue(couponEventId, userDetails.getUserId()));
    }

    @Operation(summary = "쿠폰 이벤트 생성", description = "응답은 이벤트 ID다.")
    @ApiErrorCodes({})
    @PostMapping("/events")
    public ResponseEntity<Long> createCouponEvent(@Valid @RequestBody CreateCouponEventReq req) {
        return ResponseEntity
                .ok(couponService.createCouponEvent(req));
    }
}
