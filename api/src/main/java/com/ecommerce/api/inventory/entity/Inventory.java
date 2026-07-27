package com.ecommerce.api.inventory.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.product.entity.Product;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "inventory",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_product", columnNames = "product_id")
        }
)
public class Inventory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Min(0)
    @Column(nullable = false)
    private int quantity;

    public Inventory(Product product, int quantity) {
        validate(quantity);

        this.product = product;
        this.quantity = quantity;
    }

    public void update(int newQuantity) {
        validate(newQuantity);

        this.quantity = newQuantity;
    }

    public void adjust(int delta) {
        int newQuantity = quantity + delta;
        validate(newQuantity);

        this.quantity = newQuantity;
    }

    private void validate(int quantity) {
        if (quantity < 0)
            throw new AppException(ErrorCode.INVALID_INPUT, "재고 수량은 0개 이상이어야 합니다.");
    }
}
