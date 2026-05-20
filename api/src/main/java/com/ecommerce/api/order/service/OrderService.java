package com.ecommerce.api.order.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.coupon.entity.OrderItemCoupon;
import com.ecommerce.api.coupon.service.OrderItemCouponService;
import com.ecommerce.api.order.dto.OrderDetailRes;
import com.ecommerce.api.order.dto.OrderItemListRes;
import com.ecommerce.api.order.dto.OrderListRes;
import com.ecommerce.api.order.entity.Order;
import com.ecommerce.api.order.repository.OrderRepository;
import com.ecommerce.api.product.support.ProductImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.ecommerce.api.common.exception.ErrorCode.ORDER_ACCESS_DENIED;
import static com.ecommerce.api.common.exception.ErrorCode.ORDER_NOT_FOUND;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductImageUrlResolver productImageUrlResolver;
    private final OrderItemCouponService orderItemCouponService;

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
        // 의도 명시 및 추가 조회쿼리 방지를 위해 별도 조회 메서드 사용
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new AppException(ORDER_NOT_FOUND));

        if (!order.getBuyer().getId().equals(userId))
            throw new AppException(ORDER_ACCESS_DENIED);

        Map<Long, OrderItemCoupon> orderItemCouponByOrderItemId =
                orderItemCouponService.findByOrderItemId(order.getItemList());

        List<OrderItemListRes> itemList = order.getItemList().stream()
                .map(item -> {
                    String thumbnail = productImageUrlResolver.resolveThumbnail(item.getProduct());

                    return new OrderItemListRes(item, thumbnail, orderItemCouponByOrderItemId.get(item.getId()));
                })
                .toList();

        return new OrderDetailRes(order, itemList);
    }
}
