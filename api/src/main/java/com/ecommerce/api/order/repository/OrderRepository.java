package com.ecommerce.api.order.repository;

import com.ecommerce.api.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerIdOrderByOrderedAtDesc(Long buyerId);
}
