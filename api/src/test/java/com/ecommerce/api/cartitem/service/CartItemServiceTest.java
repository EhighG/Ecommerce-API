package com.ecommerce.api.cartitem.service;

import com.ecommerce.api.cartitem.dto.AddItemReq;
import com.ecommerce.api.cartitem.dto.CartItemListRes;
import com.ecommerce.api.cartitem.dto.CartItemProductDto;
import com.ecommerce.api.cartitem.dto.ChangeQuantityReq;
import com.ecommerce.api.cartitem.entity.CartItem;
import com.ecommerce.api.cartitem.repository.CartItemRepository;
import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.media.service.MediaService;
import com.ecommerce.api.product.dto.ProductListDto;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.service.ProductService;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.ecommerce.api.support.UnitTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartItemServiceTest {

    @Mock
    CartItemRepository cartItemRepository;

    @Mock
    UserService userService;

    @Mock
    ProductService productService;

    @Mock
    MediaService mediaService;

    @InjectMocks
    CartItemService cartItemService;

    @Test
    void addItem_withNewProduct_savesNewCartItemAndReturnsSavedId() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 20;
        AddItemReq req = new AddItemReq(productId, quantity);

        User buyer = buyer();
        Product product = product();

        Long savedCartItemId = 100L;

        when(userService.getUserNotDeleted(userId)).thenReturn(buyer);
        when(productService.getProduct(productId)).thenReturn(product);
        when(cartItemRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(
                cartItemWithId(savedCartItemId, quantity)
        );

        // when
        Long result = cartItemService.addItem(req, userId);

        // then
        assertThat(result).isEqualTo(savedCartItemId);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());

        CartItem savedCartItem = captor.getValue();
        assertThat(savedCartItem.getUser()).isSameAs(buyer);
        assertThat(savedCartItem.getProduct()).isSameAs(product);
        assertThat(savedCartItem.getQuantity()).isEqualTo(quantity);
    }

    @Test
    void addItem_withExistingProduct_increasesQuantityAndReturnsExistingId() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 20;
        AddItemReq req = new AddItemReq(productId, quantity);

        User buyer = buyer();
        Product product = product();

        int oldQuantity = 1;
        CartItem existingItem = cartItemWithId(100L, oldQuantity);

        when(userService.getUserNotDeleted(userId)).thenReturn(buyer);
        when(productService.getProduct(productId)).thenReturn(product);
        when(cartItemRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(existingItem));

        // when
        Long result = cartItemService.addItem(req, userId);

        // then
        assertThat(result).isEqualTo(existingItem.getId());

        assertThat(existingItem.getQuantity()).isEqualTo(oldQuantity + quantity);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_whenUserNotFound_throwsExceptionAndDoesNotSaveCartItem() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 20;
        AddItemReq req = new AddItemReq(productId, quantity);

        AppException userNotFound = new AppException(ErrorCode.USER_NOT_FOUND);
        when(userService.getUserNotDeleted(userId)).thenThrow(userNotFound);

        // when & then
        assertThatThrownBy(() -> cartItemService.addItem(req, userId))
                .isSameAs(userNotFound);

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void addItem_whenProductNotFound_throwsExceptionAndDoesNotSaveCartItem() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 20;
        AddItemReq req = new AddItemReq(productId, quantity);

        when(userService.getUserNotDeleted(userId)).thenReturn(buyer());
        AppException productNotFound = new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        when(productService.getProduct(productId)).thenThrow(productNotFound);

        // when & then
        assertThatThrownBy(() -> cartItemService.addItem(req, userId))
                .isSameAs(productNotFound);

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void findCartItems_withThumbnail_returnsCartItemsWithThumbnailUrl() {
        // given
        Long userId = 1L;
        String thumbnailObjectKey = "sampleThumbnailObjectKey";
        String thumbnailUrl = "sampleThumbnailUrl";

        CartItemProductDto item = new CartItemProductDto(
                100L,
                2,
                20_000L,
                productListDto(thumbnailObjectKey)
        );

        when(cartItemRepository.findAllByUserIdWithProduct(userId))
                .thenReturn(List.of(item));
        when(mediaService.resolveUrl(thumbnailObjectKey))
                .thenReturn(thumbnailUrl);

        // when
        List<CartItemListRes> result = cartItemService.findCartItems(userId);

        // then
        assertThat(result).hasSize(1);

        CartItemListRes cartItem = result.getFirst();
        assertThat(cartItem.id()).isEqualTo(item.cartItemId());
        assertThat(cartItem.quantity()).isEqualTo(item.quantity());

        assertThat(cartItem.product().id()).isEqualTo(item.productListDto().id());
        assertThat(cartItem.product().thumbnailImageUrl()).isEqualTo(thumbnailUrl);
    }

    @Test
    void findCartItems_withoutThumbnail_returnsCartItemsWithoutThumbnailUrl() {
        // given
        Long userId = 1L;

        CartItemProductDto item = new CartItemProductDto(
                100L,
                2,
                20_000L,
                productListDto(null)
        );

        when(cartItemRepository.findAllByUserIdWithProduct(userId))
                .thenReturn(List.of(item));

        // when
        List<CartItemListRes> result = cartItemService.findCartItems(userId);

        // then
        assertThat(result).hasSize(1);

        CartItemListRes cartItem = result.getFirst();
        assertThat(cartItem.id()).isEqualTo(item.cartItemId());
        assertThat(cartItem.quantity()).isEqualTo(item.quantity());

        assertThat(cartItem.product().thumbnailImageUrl()).isNull();

        verify(mediaService, never()).resolveUrl(any());
    }

    @Test
    void changeQuantity_whenCartItemExists_changesQuantity() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int newQuantity = 21;
        ChangeQuantityReq req = new ChangeQuantityReq(productId, newQuantity);

        CartItem existingItem = cartItem(11);

        when(userService.getUserNotDeleted(userId)).thenReturn(buyer());
        when(cartItemRepository.findByUserIdAndProductId(userId, productId))
                .thenReturn(Optional.of(existingItem));

        // when
        cartItemService.changeQuantity(req, userId);

        // then
        assertThat(existingItem.getQuantity()).isEqualTo(newQuantity);
    }

    @Test
    void changeQuantity_whenUserNotFound_throwsExceptionAndDoesNotFindCartItem() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ChangeQuantityReq req = new ChangeQuantityReq(productId, 21);

        AppException userNotFound = new AppException(ErrorCode.USER_NOT_FOUND);

        when(userService.getUserNotDeleted(userId)).thenThrow(userNotFound);

        // when & then
        assertThatThrownBy(() -> cartItemService.changeQuantity(req, userId))
                .isSameAs(userNotFound);
        verify(cartItemRepository, never()).findByUserIdAndProductId(any(), any());
    }

    @Test
    void changeQuantity_whenCartItemNotFound_throwsCartItemNotFound() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        ChangeQuantityReq req = new ChangeQuantityReq(productId, 21);

        when(userService.getUserNotDeleted(userId)).thenReturn(buyer());
        when(cartItemRepository.findByUserIdAndProductId(userId, productId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> cartItemService.changeQuantity(req, userId))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void removeItem_whenCartItemExists_deletesCartItem() {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;

        CartItem existingItem = cartItemWithId(cartItemId, 11);

        when(userService.getUserNotDeleted(userId)).thenReturn(buyer());
        when(cartItemRepository.findByIdAndUserId(cartItemId, userId))
                .thenReturn(Optional.of(existingItem));

        // when
        cartItemService.removeItem(cartItemId, userId);

        // then
        verify(cartItemRepository).delete(existingItem);
    }

    @Test
    void removeItem_whenUserNotFound_throwsExceptionAndDoesNotDelete() {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;

        AppException userNotFound = new AppException(ErrorCode.USER_NOT_FOUND);

        when(userService.getUserNotDeleted(userId)).thenThrow(userNotFound);

        // when & then
        assertThatThrownBy(() -> cartItemService.removeItem(cartItemId, userId))
                .isSameAs(userNotFound);
        verify(cartItemRepository, never()).delete(any(CartItem.class));
    }

    @Test
    void removeItem_whenCartItemNotFound_throwsCartItemNotFoundAndDoesNotDelete() {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;

        when(userService.getUserNotDeleted(userId)).thenReturn(buyer());
        when(cartItemRepository.findByIdAndUserId(cartItemId, userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> cartItemService.removeItem(cartItemId, userId))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);

        verify(cartItemRepository, never()).delete(any(CartItem.class));
    }

    private ProductListDto productListDto(String thumbnailObjectKey) {
        return new ProductListDto(
                10L,
                "productName",
                "categoryName",
                thumbnailObjectKey,
                1000L,
                1L,
                "sellerNickname",
                5,
                4.5
        );
    }
}