package com.ecommerce.api.payment.dto;

import jakarta.validation.constraints.NotNull;

public record CompletePaymentReq(
        @NotNull Long orderId
) {}
