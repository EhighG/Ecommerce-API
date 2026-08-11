package com.ecommerce.api.cartitem.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.entity.ProductCategory;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;


class CartItemTest {

    @Test
    void constructor_withPositiveQuantity_createsCartItem() {
        // given
        User buyer = createBuyer();
        Product product = createProduct();
        int quantity = 1;

        // when
        CartItem cartItem = new CartItem(buyer, product, quantity);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(quantity);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_withNonPositiveQuantity_throwsException(int invalidQuantity) {
        // given
        User buyer = createBuyer();
        Product product = createProduct();

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> new CartItem(buyer, product, invalidQuantity))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.ORDER_QUANTITY_MUST_PLUS);
    }

    @Test
    void changeQuantity_toPositiveQuantity_changesQuantity() {
        // given
        CartItem cartItem = createCartItem(1);
        int newQuantity = 20;

        // when
        cartItem.changeQuantity(newQuantity);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(newQuantity);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void changeQuantity_toNonPositiveQuantity_throwsException(int invalidQuantity) {
        // given
        CartItem cartItem = createCartItem(1);

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> cartItem.changeQuantity(invalidQuantity))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.ORDER_QUANTITY_MUST_PLUS);
    }

    @Test
    void adjustQuantity_whenResultIsPositive_adjustsQuantity() {
        // given
        CartItem cartItem = createCartItem(5);

        // when
        cartItem.adjustQuantity(-4);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(1);
    }

    @Test
    void adjustQuantity_whenResultIsNonPositive_throwsException() {
        // given
        CartItem cartItem = createCartItem(1);

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> cartItem.adjustQuantity(-1))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.ORDER_QUANTITY_MUST_PLUS);
    }

    private User createBuyer() {
        return createUser(UserRole.BUYER);
    }

    private User createSeller() {
        return createUser(UserRole.SELLER);
    }

    private User createUser(UserRole role) {
        String roleName = role.name().toLowerCase();
        return User.join(roleName + "email", roleName + "nickname", "encPassword", role);
    }

    private Product createProduct() {
        return Product.register("productName", new ProductCategory("productCategory"), "description",
                1000L, createSeller());
    }

    private CartItem createCartItem(int quantity) {
        return new CartItem(createBuyer(), createProduct(), quantity);
    }
}