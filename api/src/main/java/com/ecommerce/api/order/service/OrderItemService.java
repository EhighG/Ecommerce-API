package com.ecommerce.api.order.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.order.dto.OrderItemDetailRes;
import com.ecommerce.api.order.dto.OrderItemListRes;
import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.enums.OrderStatus;
import com.ecommerce.api.order.repository.OrderItemRepository;
import com.ecommerce.api.order.repository.OrderRepository;
import com.ecommerce.api.product.support.ProductImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ProductImageUrlResolver productImageUrlResolver;
    private final InventoryService inventoryService;

    public List<OrderItemListRes> findByOrder(Long orderId, Long userId) {
        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);

        if (items.isEmpty()) {
            // 실제로 주문항목 없는 주문이 있는 경우
            if (orderRepository.findById(orderId).isPresent()) {
                throw new IllegalStateException("Empty Order.");
            }
            // 주문 id가 잘못된경우
            throw new AppException(ORDER_NOT_FOUND);
        }


        if (!items.getFirst().getOrder().getBuyer().getId().equals(userId))
            throw new AppException(ORDER_ACCESS_DENIED);

        return items.stream()
                .map(item -> {
                    String thumbnailUrl = productImageUrlResolver.resolveThumbnail(item.getProduct());
                    return new OrderItemListRes(item, thumbnailUrl);
                })
                .toList();
    }

    // seller 입장에서의 주문항목 조회도 있어야함
    public OrderItem getOrderItem(Long orderItemId) {
        return orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new AppException(ORDER_ITEM_NOT_FOUND));
    }

    // user가 구매자이거나, 해당 상품 판매자일때만 허용
    public OrderItemDetailRes getOrderItemDetail(Long orderItemId, Long userId) {
        OrderItem orderItem = getOrderItem(orderItemId);

        boolean isBuyer = orderItem.getOrder().getBuyer().getId().equals(userId);
        boolean isSeller = orderItem.getProduct().getSeller().getId().equals(userId);

        if (!isBuyer && !isSeller)
            throw new AppException(ORDER_ACCESS_DENIED);

        return new OrderItemDetailRes(orderItem, productImageUrlResolver.resolveThumbnail(orderItem.getProduct()));
    }

    @Transactional
    public void ship(Long orderItemId, Long sellerId) {
        OrderItem orderItem = getOrderItem(orderItemId);
        if (!isSeller(orderItem, sellerId))
            throw new AppException(ORDER_ACCESS_DENIED);

        orderItem.startDelivery();
    }

    @Transactional
    public void deliver(Long orderItemId, Long sellerId) {
        OrderItem orderItem = getOrderItem(orderItemId);
        if (!isSeller(orderItem, sellerId))
            throw new AppException(ORDER_ACCESS_DENIED);

        orderItem.delivered();
    }

    @Transactional
    public void confirm(Long orderItemId, Long buyerId) {
        OrderItem orderItem = getOrderItem(orderItemId);
        if (!isBuyer(orderItem, buyerId))
            throw new AppException(ORDER_ACCESS_DENIED);

        orderItem.confirm();
    }

    // 구매자/판매자 둘 다 가능, 주문완료 건만 가능
    @Transactional
    public void cancel(Long orderItemId, Long userId) {
        OrderItem orderItem = getOrderItem(orderItemId);
        if (!isSeller(orderItem, userId) && !isBuyer(orderItem, userId))
            throw new AppException(ORDER_ACCESS_DENIED);

        orderItem.cancel();
        inventoryService.restore(orderItem.getProduct().getId(), orderItem.getQuantity());
    }

    private boolean isSeller(OrderItem orderItem, Long userId) {
        return orderItem.getProduct().getSeller().getId().equals(userId);
    }

    private boolean isBuyer(OrderItem orderItem, Long userId) {
        return orderItem.getOrder().getBuyer().getId().equals(userId);
    }

    public boolean confirmedOrderItemExists(Long buyerId, Long productId) {
        return orderItemRepository
                .existsByOrderBuyerIdAndProductIdAndStatus(buyerId, productId, OrderStatus.PURCHASE_CONFIRMED);
    }
}
