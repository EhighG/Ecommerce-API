package com.ecommerce.api.order.dto;

import com.ecommerce.api.coupon.entity.OrderItemCoupon;
import com.ecommerce.api.coupon.entity.UsedCouponSnapshot;
import com.ecommerce.api.coupon.enums.CouponType;
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
        UsedCouponSummary usedCoupon,
        long finalLinePrice,
        OrderStatus status,
        Instant deliveredAt
) {
    public OrderItemListRes(OrderItem orderItem, String productThumbnailUrl) {
        this(orderItem, productThumbnailUrl, null);
    }

    public OrderItemListRes(OrderItem orderItem, String productThumbnailUrl, OrderItemCoupon orderItemCoupon) {
        this(
                orderItem.getId(),
                new ProductSummary(orderItem.getProduct(), productThumbnailUrl),
                orderItem.getQuantity(),
                orderItem.getLinePrice(),
                UsedCouponSummary.from(orderItemCoupon),
                calculateFinalLinePrice(orderItem, orderItemCoupon),
                orderItem.getStatus(),
                orderItem.getDeliveredAt()
        );
    }

    private static long calculateFinalLinePrice(OrderItem orderItem, OrderItemCoupon orderItemCoupon) {
        if (orderItemCoupon == null) {
            return orderItem.getLinePrice();
        }
        return orderItem.getLinePrice() - orderItemCoupon.getUsedCoupon().getDiscountedAmount();
    }

    public record UsedCouponSummary(
            Long couponIssuedId,
            String name,
            CouponType type,
            long discountValue,
            long maxDiscountAmount,
            long discountAmount,
            Instant usedAt
    ) {
        public static UsedCouponSummary from(OrderItemCoupon orderItemCoupon) {
            if (orderItemCoupon == null) {
                return null;
            }

            UsedCouponSnapshot usedCoupon = orderItemCoupon.getUsedCoupon();
            return new UsedCouponSummary(
                    usedCoupon.getCouponIssuedId(),
                    usedCoupon.getName(),
                    usedCoupon.getType(),
                    usedCoupon.getDiscountValue(),
                    usedCoupon.getMaxDiscountAmount(),
                    usedCoupon.getDiscountedAmount(),
                    usedCoupon.getUsedAt()
            );
        }
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
