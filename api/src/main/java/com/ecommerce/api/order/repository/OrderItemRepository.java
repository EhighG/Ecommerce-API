package com.ecommerce.api.order.repository;

import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("""
            select case when count(oi) > 0 then true else false end
            from OrderItem oi
            where oi.product.seller.id = :sellerId
            and oi.status in :statuses
            """)
    boolean existsByProductSellerIdAndStatuses(Long sellerId, List<OrderStatus> statuses);

    @Query("""
select case when count(oi) > 0 then true else false end
from OrderItem oi
join oi.order o
where oi.product.id = :productId
and o.buyer.id = :buyerId
and oi.status = :status
""")
    boolean existsByOrderBuyerIdAndProductIdAndStatus(Long buyerId, Long productId, OrderStatus status);

    List<OrderItem> findAllByOrderId(Long orderId);
}
