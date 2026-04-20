package com.ecommerce.api.chatroom.repository;

import com.ecommerce.api.chatroom.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findByBuyerIdAndBuyerLeftAtIsNull(Long buyerId);
    List<ChatRoom> findByProductSellerIdAndSellerLeftAtIsNull(Long sellerId);

    @Query("""
            select cr
            from ChatRoom cr
            join cr.product p
            where p.id = :productId
            and p.deleted = false
            and cr.buyer.id = :buyerId
            and cr.sellerLeftAt is null
            and cr.buyerLeftAt is null
            """)
    ChatRoom findActiveByProductIdAndBuyerId(Long productId, Long buyerId);
}
