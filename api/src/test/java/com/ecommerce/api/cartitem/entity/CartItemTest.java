package com.ecommerce.api.cartitem.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecommerce.api.support.UnitTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;


class CartItemTest {

    @Test
    void constructor_withPositiveQuantity_createsCartItem() {
        // given
        User buyer = buyer();
        Product product = product();
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
        User buyer = buyer();
        Product product = product();

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> new CartItem(buyer, product, invalidQuantity))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.ORDER_QUANTITY_MUST_PLUS);
    }

    @Test
    void changeQuantity_toPositiveQuantity_changesQuantity() {
        // given
        CartItem cartItem = cartItem(1);
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
        CartItem cartItem = cartItem(1);

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> cartItem.changeQuantity(invalidQuantity))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.ORDER_QUANTITY_MUST_PLUS);
    }

    @Test
    void adjustQuantity_whenResultIsPositive_adjustsQuantity() {
        // given
        CartItem cartItem = cartItem(5);

        // when
        cartItem.adjustQuantity(-4);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(1);
    }

    @Test
    void adjustQuantity_whenResultIsNonPositive_throwsException() {
        // given
        CartItem cartItem = cartItem(1);

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> cartItem.adjustQuantity(-1))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.ORDER_QUANTITY_MUST_PLUS);
    }
}