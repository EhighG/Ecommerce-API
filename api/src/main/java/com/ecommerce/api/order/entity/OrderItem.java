package com.ecommerce.api.order.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.order.enums.OrderStatus;
import com.ecommerce.api.order.vo.ProductSnapshot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static com.ecommerce.api.common.exception.ErrorCode.WRONG_STATUS_CHANGE;
import static com.ecommerce.api.order.enums.OrderStatus.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Embedded
    private ProductSnapshot product;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long linePrice;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant deliveredAt;

    public OrderItem(ProductSnapshot product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.linePrice = product.getUnitPrice() * quantity;
        this.status = ORDERED;
    }

    public void assignOrder(Order order) {
        this.order = order;
    }

    public void startDelivery() {
        if (this.status != ORDERED)
            throw new AppException(WRONG_STATUS_CHANGE, "wrong orderStatus change. (" + this.status + " -> " + SHIPPED + ")");
        this.status = SHIPPED;
    }

    public void delivered() {
        if (this.status != SHIPPED)
            throw new AppException(WRONG_STATUS_CHANGE, "wrong orderStatus change. (" + this.status + " -> " + DELIVERED + ")");
        this.status = DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public void confirm() {
        if (this.status != DELIVERED)
            throw new AppException(WRONG_STATUS_CHANGE, "wrong orderStatus change. (" + this.status + " -> " + PURCHASE_CONFIRMED + ")");
        this.status = PURCHASE_CONFIRMED;
    }

    public void cancel() {
        if (this.status != ORDERED)
            throw new AppException(WRONG_STATUS_CHANGE, "주문완료 상태인 주문만 취소 가능합니다.");
        this.status = CANCELED;
    }
}
