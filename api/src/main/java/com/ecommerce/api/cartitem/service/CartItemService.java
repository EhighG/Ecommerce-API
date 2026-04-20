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
import com.ecommerce.api.product.dto.ProductListRes;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.service.ProductService;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final UserService userService;
    private final ProductService productService;
    private final MediaService mediaService;

    @Transactional
    public Long addItem(AddItemReq req, Long userId) {
        User buyer = userService.getUserNotDeleted(userId);
        Product product = productService.getProduct(req.productId());

        CartItem cartItem = cartItemRepository.findByUserIdAndProductId(userId, product.getId())
                .orElse(null);

        if (cartItem != null) {
            cartItem.adjustQuantity(req.quantity());
            return cartItem.getId();
        }

        CartItem saved = cartItemRepository.save(
                new CartItem(buyer, product, req.quantity())
        );

        return saved.getId();
    }

    public List<CartItemListRes> findCartItems(Long userId) {
        User buyer = userService.getUserNotDeleted(userId);

        return cartItemRepository.findAllByUserIdWithProduct(userId).stream()
                .map(this::toCartItemListRes)
                .toList();
    }

    private CartItemListRes toCartItemListRes(CartItemProductDto item) {
        String productThumbnailUrl = null;
        if (item.productListDto().thumbnailObjectKey() != null) {
            productThumbnailUrl = mediaService.resolveUrl(item.productListDto().thumbnailObjectKey());
        }

        return new CartItemListRes(
                item.cartItem().getId(),
                ProductListRes.forCart(item.productListDto(), productThumbnailUrl),
                item.cartItem().getQuantity(),
                item.cartItem().getLinePrice()
        );
    }

    @Transactional
    public void changeQuantity(ChangeQuantityReq req, Long userId) {
        User user = userService.getUserNotDeleted(userId);

        CartItem cartItem = cartItemRepository.findByUserIdAndProductId(userId, req.productId())
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        cartItem.changeQuantity(req.quantity());
    }

    @Transactional
    public void removeItem(Long cartItemId, Long userId) {
        User buyer = userService.getUserNotDeleted(userId);

        CartItem cartItem = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        cartItemRepository.delete(cartItem);
    }
}
