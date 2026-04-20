package com.ecommerce.api.payment.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.order.entity.Order;
import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.service.OrderService;
import com.ecommerce.api.payment.dto.CompletePaymentReq;
import com.ecommerce.api.payment.entity.Payment;
import com.ecommerce.api.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ecommerce.api.common.exception.ErrorCode.ORDER_ACCESS_DENIED;
import static com.ecommerce.api.common.exception.ErrorCode.PAYMENT_ALREADY_EXISTS;

@RequiredArgsConstructor
@Transactional
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    @Transactional
    public Long completePayment(CompletePaymentReq req, Long userId) {
        Order order = orderService.getOrder(req.orderId());

        if (!order.getBuyer().getId().equals(userId))
            throw new AppException(ORDER_ACCESS_DENIED);

        if (paymentRepository.findByOrderId(order.getId()).isPresent())
            throw new AppException(PAYMENT_ALREADY_EXISTS);

        inventoryService.validateAndDeduct(order.getItemList());
        order.getItemList().forEach(OrderItem::completePayment);

        Payment saved = paymentRepository.save(new Payment(order, order.getTotalPrice()));
        return saved.getId();
    }

    public void cancelPayment(Long paymentId, Long userId) {

    }
}
