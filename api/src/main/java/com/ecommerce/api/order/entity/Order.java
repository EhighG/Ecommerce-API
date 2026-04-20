package com.ecommerce.api.order.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.order.vo.OrderLine;
import com.ecommerce.api.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItem> itemList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(nullable = false)
    private long totalPrice;

    private Instant orderedAt;

    public Order(List<OrderLine> orderLineList, User buyer) {
        validateOrder(orderLineList, buyer);

        this.buyer = buyer;
        this.itemList = new ArrayList<>();
        this.orderedAt = Instant.now();

        for (OrderLine orderLine : orderLineList) {
            OrderItem orderItem = new OrderItem(orderLine.product(), orderLine.quantity());
            addItem(orderItem);
            totalPrice += orderItem.getLinePrice();
        }
    }

    private void validateOrder(List<OrderLine> orderLineList, User buyer) {
        if (orderLineList == null || orderLineList.isEmpty())
            throw new AppException(ErrorCode.INVALID_INPUT, "Order line list is empty");
        if (buyer == null)
            throw new AppException(ErrorCode.INVALID_INPUT, "Buyer is null");
    }

    private void addItem(OrderItem orderItem) {
        itemList.add(orderItem);
        orderItem.assignOrder(this);
    }
}
