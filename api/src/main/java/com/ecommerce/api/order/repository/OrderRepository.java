package com.ecommerce.api.order.repository;

import com.ecommerce.api.order.dto.OrderListDto;
import com.ecommerce.api.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerIdOrderByOrderedAtDesc(Long buyerId);

    @Query("""
            select new com.ecommerce.api.order.dto.OrderListDto(
                o.id,
                o.totalPrice,
                count(oi),
                o.orderedAt
            )
            from Order o
            left join o.itemList oi
            where o.buyer.id = :buyerId
            group by o.id, o.totalPrice, o.orderedAt
            order by o.orderedAt desc
            """)
    List<OrderListDto> findOrderListByBuyerId(Long buyerId);

    boolean existsByIdAndBuyerId(Long id, Long buyerId);
}
