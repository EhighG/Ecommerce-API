package com.ecommerce.api.payment.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.order.entity.Order;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 연동 등은 생략하고, 결제 데이터 생성까지만 구현(세부정보 제외)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "payment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_order", columnNames = "order_id")
        }
)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Min(0)
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private boolean canceled = false;

    public Payment(Order order, long amount) {
        if (amount < 0)
            throw new AppException(ErrorCode.INVALID_INPUT, "amount must be same or greater than 0");
        this.order = order;
        this.amount = amount;
        order.completeOrder();
    }

    public void cancel() {
        this.canceled = true;
    }
}
