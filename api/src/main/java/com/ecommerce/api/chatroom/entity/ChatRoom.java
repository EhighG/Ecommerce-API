package com.ecommerce.api.chatroom.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Instant sellerLeftAt;

    @Column(name = "seller_last_read_chat_id", nullable = false)
    private long sellerLastReadChatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    private Instant buyerLeftAt;

    @Column(name = "buyer_last_read_chat_id", nullable = false)
    private long buyerLastReadChatId;

    public ChatRoom(Product product, User buyer) {
        // 채팅 생성은 구매자만 가능
        if (!buyer.getRole().equals(UserRole.BUYER))
            throw new AppException(ErrorCode.ONLY_BUYER_START_CHATTING);

        this.product = product;
        this.buyer = buyer;
        this.sellerLastReadChatId = 0L;
        this.buyerLastReadChatId = 0L;
    }

    public void updateSellerLastRead(long chatId) {
        if (chatId > sellerLastReadChatId)
            this.sellerLastReadChatId = chatId;
    }

    public void updateBuyerLastRead(long chatId) {
        if (chatId > buyerLastReadChatId)
            this.buyerLastReadChatId = chatId;
    }

    /*
    seller/buyer leave 동작
    둘 다 나갔는지 체크해서 delete하는 동작은 서비스 단에서 처리
     */

    public void sellerLeave() {
        this.sellerLeftAt = Instant.now();
    }

    public void buyerLeave() {
        this.buyerLeftAt = Instant.now();
    }

    public boolean noUserRemaining() {
        return sellerLeftAt != null && buyerLeftAt != null;
    }
}
