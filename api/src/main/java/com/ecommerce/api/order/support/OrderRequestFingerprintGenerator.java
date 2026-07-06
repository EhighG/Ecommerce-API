package com.ecommerce.api.order.support;

import com.ecommerce.api.common.util.Sha256Hasher;
import com.ecommerce.api.idempotency.support.RequestFingerprintGenerator;
import com.ecommerce.api.order.dto.OrderReq;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.Collectors;

@Component
public class OrderRequestFingerprintGenerator implements RequestFingerprintGenerator<OrderReq> {

    @Override
    public String generate(OrderReq req) {
        String canonical = req.items().stream()
                .sorted(Comparator.comparingLong(OrderReq.OrderItemReq::cartItemId))
                .map(this::canonicalize)
                .collect(Collectors.joining("|"));

        return Sha256Hasher.hash(canonical);
    }

    private String canonicalize(OrderReq.OrderItemReq item) {
        String couponIssuedId = item.couponIssuedId() == null ? "null" : item.couponIssuedId().toString();

        return "cartItemId=" + item.cartItemId()
                + ";orderQuantity=" + item.orderQuantity()
                + ";couponIssuedId=" + couponIssuedId;
    }
}
