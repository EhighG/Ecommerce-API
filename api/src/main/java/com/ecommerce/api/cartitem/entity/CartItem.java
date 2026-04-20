package com.ecommerce.api.cartitem.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "cart_item",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cart_item_user_product",
                        columnNames = {"user_id", "product_id"}
                )
        }
)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Min(1)
    @Column(nullable = false)
    private int quantity;

    public CartItem(User user, Product product, int quantity) {
        if (quantity <= 0)
            throw new AppException(ErrorCode.ORDER_QUANTITY_MUST_PLUS);

        this.user = user;
        this.product = product;
        this.quantity = quantity;
    }

    public long getLinePrice() {
        return product.getUnitPrice() * quantity;
    }

    public void changeQuantity(int newQuantity) {
        if (newQuantity <= 0)
            throw new AppException(ErrorCode.ORDER_QUANTITY_MUST_PLUS);

        this.quantity = newQuantity;
    }

    public void adjustQuantity(int delta) {
        changeQuantity(quantity + delta);
    }
}
