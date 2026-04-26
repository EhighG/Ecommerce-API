package com.ecommerce.api.order.service;

import com.ecommerce.api.cartitem.entity.CartItem;
import com.ecommerce.api.cartitem.repository.CartItemRepository;
import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.order.dto.OrderDetailRes;
import com.ecommerce.api.order.dto.OrderItemListRes;
import com.ecommerce.api.order.dto.OrderListRes;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.entity.Order;
import com.ecommerce.api.order.repository.OrderRepository;
import com.ecommerce.api.order.vo.OrderLine;
import com.ecommerce.api.order.vo.ProductSnapshot;
import com.ecommerce.api.product.support.ProductImageUrlResolver;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final ProductImageUrlResolver productImageUrlResolver;
    private final CartItemRepository cartItemRepository;
    private final InventoryService inventoryService;

    @Transactional
    public Long order(OrderReq req, Long userId) {
        User buyer = userService.getUserNotDeleted(userId);

        List<CartItem> cartItemList = cartItemRepository.findAllByUserIdAndIdInWithProduct(userId, req.cartItemIdList());
        if (cartItemList.size() != req.cartItemIdList().size()) {
            throw new AppException(CART_ITEM_NOT_FOUND);
        }

        checkProductNotDeleted(cartItemList);
        inventoryService.validateAndDeduct(cartItemList);

        List<OrderLine> orderLines = cartItemList.stream()
                .map(cartItem -> {
                    ProductSnapshot productSnapshot = ProductSnapshot.from(cartItem.getProduct());
                    return new OrderLine(productSnapshot, cartItem.getQuantity());
                }).toList();

        Order saved = orderRepository.save(new Order(orderLines, buyer));
        cartItemRepository.deleteAllByUserIdAndIdIn(userId, req.cartItemIdList());

        return saved.getId();
    }

    private void checkProductNotDeleted(List<CartItem> cartItemList) {
        for (CartItem cartItem : cartItemList) {
            if (cartItem.getProduct().isDeleted()) {
                throw new AppException(DELETED_PRODUCT);
            }
        }
    }

    public List<OrderListRes> findByBuyerId(Long buyerId) {
        return orderRepository.findOrderListByBuyerId(buyerId).stream()
                .map(OrderListRes::new)
                .toList();
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ORDER_NOT_FOUND));
    }

    public OrderDetailRes getMyOrderDetail(Long orderId, Long userId) {
        Order order = getOrder(orderId);

        if (!order.getBuyer().getId().equals(userId))
            throw new AppException(ORDER_ACCESS_DENIED);

        List<OrderItemListRes> itemList = order.getItemList().stream()
                .map(item -> {
                    String thumbnail = productImageUrlResolver.resolveThumbnail(item.getProduct());

                    return new OrderItemListRes(item, thumbnail);
                })
                .toList();

        return new OrderDetailRes(order, itemList);
    }
}
