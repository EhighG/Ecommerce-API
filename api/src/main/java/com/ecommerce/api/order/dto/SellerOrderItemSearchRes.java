package com.ecommerce.api.order.dto;

import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.enums.OrderStatus;
import com.ecommerce.api.order.vo.ProductSnapshot;
import com.ecommerce.api.user.dto.UserSummary;

import java.util.List;

public record SellerOrderItemSearchRes(
        List<OrderItemSummary> items,
        int page,
        int size,
        long totalCount,
        int totalPages,
        boolean hasNext
) implements OrderItemSearchRes {
    public record OrderItemSummary(
            Long orderItemId,
            ProductSummary product,
            int quantity,
            long linePrice,
            UserSummary buyer,
            OrderStatus status
    ) {
        public OrderItemSummary(OrderItem orderItem, String thumbnailUrl) {
            this(
                    orderItem.getId(),
                    new ProductSummary(orderItem.getProduct(), thumbnailUrl),
                    orderItem.getQuantity(),
                    orderItem.getLinePrice(),
                    new UserSummary(orderItem.getOrder().getBuyer()),
                    orderItem.getStatus()
            );
        }
    }

    public record ProductSummary(
            Long id,
            String name,
            String thumbnailUrl,
            long unitPrice
    ) {
        public ProductSummary(ProductSnapshot product, String thumbnailUrl) {
            this(
                    product.getId(),
                    product.getName(),
                    thumbnailUrl,
                    product.getUnitPrice()
            );
        }
    }
}