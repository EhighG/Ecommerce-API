package com.ecommerce.api.order.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.coupon.entity.OrderItemCoupon;
import com.ecommerce.api.coupon.service.CouponService;
import com.ecommerce.api.coupon.service.OrderItemCouponService;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.order.dto.*;
import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.repository.OrderItemRepository;
import com.ecommerce.api.order.repository.OrderRepository;
import com.ecommerce.api.product.repository.ProductStatRepository;
import com.ecommerce.api.product.support.ProductImageUrlResolver;
import com.ecommerce.api.user.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ProductStatRepository productStatRepository;
    private final ProductImageUrlResolver productImageUrlResolver;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final OrderItemCouponService orderItemCouponService;

    public OrderItemSearchRes search(OrderItemSearchReq req, Long userId, UserRole role, Pageable pageable) {
        if (req.ofBuyer()) {
            if (!UserRole.BUYER.equals(role)) {
                throw new AppException(NO_PERMISSIONS);
            }
            return searchForBuyer(req, userId, pageable);
        } else {
            if (!UserRole.SELLER.equals(role)) {
                throw new AppException(NO_PERMISSIONS);
            }
            return searchForSeller(req, userId, pageable);
        }
    }

    public OrderItemSearchRes searchForBuyer(OrderItemSearchReq req, Long userId, Pageable pageable) {
        if (!orderRepository.existsByIdAndBuyerId(req.orderId(), userId)) {
            throw new AppException(ORDER_NOT_FOUND);
        }

        Page<OrderItem> page = orderItemRepository.findAllByOrderAndBuyer(req.orderId(), userId, pageable);
        Map<Long, OrderItemCoupon> orderItemCouponByOrderItemId =
                orderItemCouponService.findByOrderItemId(page.getContent());

        List<BuyerOrderItemSearchRes.OrderItemSummary> content = page.getContent().stream()
                .map(item -> {
                    String thumbnailUrl = productImageUrlResolver.resolveThumbnail(item.getProduct());
                    return new BuyerOrderItemSearchRes.OrderItemSummary(
                            item,
                            thumbnailUrl,
                            orderItemCouponByOrderItemId.get(item.getId())
                    );
                })
                .toList();

        return new BuyerOrderItemSearchRes(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    public OrderItemSearchRes searchForSeller(OrderItemSearchReq req, Long userId, Pageable pageable) {
        if (!req.sellerId().equals(userId)) {
            throw new AppException(ORDER_ACCESS_DENIED);
        }

        Page<OrderItem> page;
        if (req.hasStatusCondition()) {
            page = orderItemRepository.findAllBySellerAndStatusIn(req.sellerId(), req.statusList(), pageable);
        } else {
            page = orderItemRepository.findAllBySeller(req.sellerId(), pageable);
        }
        Map<Long, OrderItemCoupon> orderItemCouponByOrderItemId =
                orderItemCouponService.findByOrderItemId(page.getContent());

        List<SellerOrderItemSearchRes.OrderItemSummary> content = page.getContent().stream()
                .map(item -> {
                    String thumbnailUrl = productImageUrlResolver.resolveThumbnail(item.getProduct());
                    return new SellerOrderItemSearchRes.OrderItemSummary(
                            item,
                            thumbnailUrl,
                            orderItemCouponByOrderItemId.get(item.getId())
                    );
                })
                .toList();

        return new SellerOrderItemSearchRes(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
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

        OrderItemCoupon orderItemCoupon = orderItemCouponService.findByOrderItemId(orderItemId);

        return new OrderItemDetailRes(
                orderItem,
                productImageUrlResolver.resolveThumbnail(orderItem.getProduct()),
                orderItemCoupon
        );
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

        // 사용했던 쿠폰 있으면 복구
        OrderItemCoupon usedCoupon = orderItemCouponService.findByOrderItemId(orderItemId);
        if (usedCoupon != null) {
            Long couponIssuedId = usedCoupon.getUsedCoupon().getCouponIssuedId();
            couponService.restoreCouponIssued(couponIssuedId, Instant.now());
        }

        int updatedCount = productStatRepository.increaseOrderItemCount(orderItem.getProduct().getId(), -1L);
        if (updatedCount != 1) {
            throw new AppException(PRODUCT_STAT_NOT_FOUND);
        }

        inventoryService.restore(orderItem.getProduct().getId(), orderItem.getQuantity());
    }

    private boolean isSeller(OrderItem orderItem, Long userId) {
        return orderItem.getProduct().getSeller().getId().equals(userId);
    }

    private boolean isBuyer(OrderItem orderItem, Long userId) {
        return orderItem.getOrder().getBuyer().getId().equals(userId);
    }
}
