package com.ecommerce.api.coupon.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.coupon.dto.CouponEventDetailRes;
import com.ecommerce.api.coupon.dto.CreateCouponEventReq;
import com.ecommerce.api.coupon.dto.IssueCouponRes;
import com.ecommerce.api.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/coupons")
@RestController
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/events/{couponEventId}")
    public ResponseEntity<CouponEventDetailRes> getCouponEventDetail(@PathVariable Long couponEventId) {
        return ResponseEntity
                .ok(couponService.getCouponEventDetail(couponEventId));
    }

    @PostMapping("/events/{couponEventId}/issue")
    public ResponseEntity<IssueCouponRes> issueCoupon(@PathVariable Long couponEventId,
                                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(couponService.issue(couponEventId, userDetails.getUserId()));
    }

    @PostMapping("/events")
    public ResponseEntity<Long> createCouponEvent(@Valid @RequestBody CreateCouponEventReq req) {
        return ResponseEntity
                .ok(couponService.createCouponEvent(req));
    }
}
