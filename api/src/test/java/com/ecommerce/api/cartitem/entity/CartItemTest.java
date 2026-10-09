package com.ecommerce.api.cartitem.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecommerce.api.support.UnitTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;


class CartItemTest {

    @Test
    @DisplayName("수량이 1 이상이면 장바구니 항목을 만든다")
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
    @DisplayName("수량이 0 이하면 장바구니 항목을 만들지 않고 수량 오류를 반환한다")
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
    @DisplayName("1 이상의 수량으로 바꾸면 수량을 바꾼다")
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
    @DisplayName("0 이하의 수량으로 바꾸면 수량 오류를 반환한다")
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
    @DisplayName("조정한 결과가 1 이상이면 수량을 조정한다")
    void adjustQuantity_whenResultIsPositive_adjustsQuantity() {
        // given
        CartItem cartItem = cartItem(5);

        // when
        cartItem.adjustQuantity(-4);

        // then
        assertThat(cartItem.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("조정한 결과가 0 이하면 수량 오류를 반환한다")
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