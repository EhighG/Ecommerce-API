package com.ecommerce.api.order.dto;

import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.enums.OrderStatus;
import com.ecommerce.api.order.vo.ProductSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderItemListRes(
        Long orderItemId,
        ProductSummary product,
        int quantity,
        long linePrice,
        OrderStatus status,
        Instant deliveredAt
) {
    public OrderItemListRes(OrderItem orderItem, String productThumbnailUrl) {
        this(
                orderItem.getId(),
                new ProductSummary(orderItem.getProduct(), productThumbnailUrl),
                orderItem.getQuantity(),
                orderItem.getLinePrice(),
                orderItem.getStatus(),
                orderItem.getDeliveredAt()
        );
    }

    public record ProductSummary(
            Long productId,
            String name,
            String thumbnailUrl,
            UserSummary seller
    ) {
        public ProductSummary(ProductSnapshot product, String thumbnailUrl) {
            this(
                    product.getId(),
                    product.getName(),
                    thumbnailUrl,
                    new UserSummary(product.getSeller().getId(), product.getSeller().getNickname())
            );
        }
    }

    public record UserSummary(
            Long id,
            String nickname
    ) {}
}
