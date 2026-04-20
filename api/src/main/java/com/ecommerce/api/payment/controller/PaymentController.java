package com.ecommerce.api.payment.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.payment.dto.CompletePaymentReq;
import com.ecommerce.api.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/payments")
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Long> completePayment(@Valid @RequestBody CompletePaymentReq req,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(paymentService.completePayment(req, userDetails.getUserId()));
    }
}
