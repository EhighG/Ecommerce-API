package com.ecommerce.api.order.service;

import com.ecommerce.api.cartitem.entity.CartItem;
import com.ecommerce.api.cartitem.repository.CartItemRepository;
import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.coupon.entity.CouponIssued;
import com.ecommerce.api.coupon.service.CouponService;
import com.ecommerce.api.coupon.service.OrderItemCouponService;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.entity.Order;
import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.repository.OrderRepository;
import com.ecommerce.api.order.vo.OrderLine;
import com.ecommerce.api.order.vo.ProductSnapshot;
import com.ecommerce.api.product.repository.ProductStatRepository;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Service
public class OrderPlacementService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductStatRepository productStatRepository;
    private final UserService userService;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final OrderItemCouponService orderItemCouponService;

    @Transactional
    public Long placeOrder(OrderReq req, Long userId) {
        User buyer = userService.getUserNotDeleted(userId);

        Map<Long, CartItem> cartItemById = getCartItems(req, userId);
        Map<Long, CouponIssued> couponById = getCoupons(req, userId);

        Instant now = Instant.now();

        List<OrderLine> orderLines = req.items().stream()
                .map(item -> {
                    CartItem cartItem = cartItemById.get(item.cartItemId());

                    int quantity = item.orderQuantity();
                    ProductSnapshot productSnapshot = ProductSnapshot.from(cartItem.getProduct());

                    Long couponIssuedId = item.couponIssuedId();
                    long discountAmount = 0L;
                    if (couponIssuedId != null) {
                        CouponIssued coupon = couponById.get(couponIssuedId);

                        long linePrice = productSnapshot.getUnitPrice() * quantity;

                        discountAmount = coupon.getCouponEvent().calculateDiscountAmount(linePrice);
                        coupon.use(now);
                    }

                    return new OrderLine(productSnapshot, quantity, couponIssuedId, discountAmount);
                }).toList();

        inventoryService.validateAndDeduct(orderLines);

        Order saved = orderRepository.save(new Order(orderLines, buyer));

        // 주문완료 후 후처리
        updateProductOrderCount(saved.getItemList());
        createUsedCouponSnapshot(orderLines, saved.getItemList(), couponById);
        deleteOrderedCartItem(req, cartItemById);

        return saved.getId();
    }

    private Map<Long, CartItem> getCartItems(OrderReq req, Long userId) {
        List<Long> cartItemIds = req.items().stream()
                .map(OrderReq.OrderItemReq::cartItemId)
                .distinct()
                .toList();

        if (cartItemIds.size() != req.items().size()) {
            throw new AppException(INVALID_INPUT, "중복된 장바구니 항목이 있습니다.");
        }

        List<CartItem> cartItems =
                cartItemRepository.findAllByUserIdAndIdInWithProduct(userId, cartItemIds);

        if (cartItems.size() != cartItemIds.size()) {
            throw new AppException(CART_ITEM_NOT_FOUND);
        }
        checkProductNotDeleted(cartItems);

        return cartItems.stream()
                .collect(Collectors.toMap(CartItem::getId, Function.identity()));
    }

    private void checkProductNotDeleted(List<CartItem> cartItems) {
        for (CartItem cartItem : cartItems) {
            if (cartItem.getProduct().isDeleted()) {
                throw new AppException(DELETED_PRODUCT);
            }
        }
    }

    private Map<Long, CouponIssued> getCoupons(OrderReq req, Long userId) {
        List<Long> couponIssuedIds = req.items().stream()
                .filter(item -> item.couponIssuedId() != null)
                .map(OrderReq.OrderItemReq::couponIssuedId)
                .toList();

        List<CouponIssued> coupons = couponService.getCoupons(couponIssuedIds, userId);

        return coupons.stream()
                .collect(Collectors.toMap(
                        CouponIssued::getId,
                        Function.identity()
                ));
    }

    private void updateProductOrderCount(List<OrderItem> orderItems) {
        List<Long> productIds = orderItems.stream()
                .map(orderItem -> orderItem.getProduct().getId())
                .toList();

        int updatedCount = productStatRepository.increaseOrderItemCountIn(productIds, 1L);
        if (updatedCount != productIds.size()) {
            throw new AppException(PRODUCT_STAT_NOT_FOUND);
        }
    }

    private void createUsedCouponSnapshot(List<OrderLine> orderLines, List<OrderItem> orderItems,
                                          Map<Long, CouponIssued> couponById) {
        for (int i = 0; i < orderLines.size(); i++) {
            OrderLine orderLine = orderLines.get(i);
            if (!orderLine.hasCoupon()) {
                continue;
            }

            OrderItem orderItem = orderItems.get(i);
            CouponIssued coupon = couponById.get(orderLine.couponIssuedId());

            orderItemCouponService.create(orderItem, coupon, orderLine.discountAmount());
        }
    }

    private void deleteOrderedCartItem(OrderReq req, Map<Long, CartItem> cartItemById) {
        req.items().forEach(item -> {
            int orderQuantity = item.orderQuantity();
            CartItem cartItem = cartItemById.get(item.cartItemId());

            if (orderQuantity >= cartItem.getQuantity()) {
                cartItemRepository.delete(cartItem);
            } else {
                cartItem.adjustQuantity(-1 * orderQuantity);
            }
        });
    }
}
