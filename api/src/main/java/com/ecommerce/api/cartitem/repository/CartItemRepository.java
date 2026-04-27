package com.ecommerce.api.cartitem.repository;

import com.ecommerce.api.cartitem.dto.CartItemProductDto;
import com.ecommerce.api.cartitem.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
    Optional<CartItem> findByIdAndUserId(Long cartItemId, Long userId);

    @Query("""
            select new com.ecommerce.api.cartitem.dto.CartItemProductDto(
                ci.id,
                ci.quantity,
                p.unitPrice * ci.quantity,
                new com.ecommerce.api.product.dto.ProductListDto(
                    p.id,
                    p.name,
                    pc.name,
                    ui.objectKey,
                    p.unitPrice,
                    ps.id,
                    ps.nickname,
                    i.quantity,
                    pst.ratingAvg
                )
            )
            from CartItem ci
            join ci.product p
            join p.category pc
            join p.seller ps
            join Inventory i on i.product = p
            join ProductStat pst on pst.product = p
            left join p.thumbnailImage ti
            left join ti.uploadedImage ui
            where ci.user.id = :userId
            and p.deleted = false
            order by ci.id desc
            """)
    List<CartItemProductDto> findAllByUserIdWithProduct(Long userId);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product p
            join fetch p.category pc
            join fetch p.seller ps
            left join fetch p.thumbnailImage ti
            left join fetch ti.uploadedImage ui
            where ci.user.id = :userId
            and ci.id in :cartItemIdList
            """)
    List<CartItem> findAllByUserIdAndIdInWithProduct(Long userId, List<Long> cartItemIdList);

    @Modifying
    @Query("""
            delete from CartItem ci
            where ci.user.id = :userId
            and ci.id in :cartItemIdList
            """)
    void deleteAllByUserIdAndIdIn(Long userId, List<Long> cartItemIdList);

    @Modifying
    @Query("""
            delete from CartItem ci
            where ci.product.id = :productId
            """)
    void deleteAllByProductId(Long productId);

    @Modifying
    @Query("""
            delete from CartItem ci
            where ci.product.id in :productIds
            """)
    void deleteAllByProductIdIn(List<Long> productIds);
}
