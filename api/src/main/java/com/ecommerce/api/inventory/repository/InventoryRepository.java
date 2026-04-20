package com.ecommerce.api.inventory.repository;

import com.ecommerce.api.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from Inventory i
            where i.product.id = :productId
            """)
    Optional<Inventory> findByProductIdForUpdate(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from Inventory i
            where i.product.id in :productIdList
            """)
    List<Inventory> findAllByProductIdInForUpdate(List<Long> productIdList);
}
