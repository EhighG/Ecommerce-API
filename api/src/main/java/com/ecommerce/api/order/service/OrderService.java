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
import com.ecommerce.api.product.repository.ProductStatRepository;
import com.ecommerce.api.product.support.ProductImageUrlResolver;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductStatRepository productStatRepository;
    private final UserService userService;
    private final ProductImageUrlResolver productImageUrlResolver;
    private final InventoryService inventoryService;

    @Transactional
    public Long order(OrderReq req, Long userId) {
        User buyer = userService.getUserNotDeleted(userId);

        List<Long> cartItemIds = req.items().stream()
                .map(OrderReq.OrderItemReq::cartItemId)
                .toList();

        if (cartItemIds.stream().distinct().count() != cartItemIds.size()) {
            throw new AppException(INVALID_INPUT, "중복된 장바구니 항목이 있습니다.");
        }

        List<CartItem> cartItemList = cartItemRepository.findAllByUserIdAndIdInWithProduct(userId, cartItemIds);
        if (cartItemList.size() != cartItemIds.size()) {
            throw new AppException(CART_ITEM_NOT_FOUND);
        }

        checkProductNotDeleted(cartItemList);

        Map<Long, Integer> orderQuantityByCartItemId = req.items().stream()
                        .collect(Collectors.toMap(
                                OrderReq.OrderItemReq::cartItemId,
                                OrderReq.OrderItemReq::orderQuantity
                        ));

        List<OrderLine> orderLines = cartItemList.stream()
                .map(cartItem -> {
                    int quantity = orderQuantityByCartItemId.get(cartItem.getId());
                    ProductSnapshot productSnapshot = ProductSnapshot.from(cartItem.getProduct());

                    return new OrderLine(productSnapshot, quantity);
                }).toList();

        inventoryService.validateAndDeduct(orderLines);

        Order saved = orderRepository.save(new Order(orderLines, buyer));

        List<Long> productIds = orderLines.stream()
                .map(orderLine -> orderLine.product().getId())
                .toList();

        int updatedCount = productStatRepository.increaseOrderItemCountIn(productIds, 1L);
        if (updatedCount != productIds.size()) {
            throw new AppException(PRODUCT_STAT_NOT_FOUND);
        }

        for (CartItem cartItem : cartItemList) {
            int orderQuantity = orderQuantityByCartItemId.get(cartItem.getId());

            if (orderQuantity >= cartItem.getQuantity()) {
                cartItemRepository.delete(cartItem);
            } else {
                cartItem.adjustQuantity(-1 * orderQuantity);
            }
        }

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
