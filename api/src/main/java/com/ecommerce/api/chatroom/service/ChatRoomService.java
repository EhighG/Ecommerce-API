package com.ecommerce.api.chatroom.service;

import com.ecommerce.api.chat.entity.Chat;
import com.ecommerce.api.chat.service.ChatService;
import com.ecommerce.api.chatroom.dto.ChatRoomDetailRes;
import com.ecommerce.api.chatroom.dto.ChatRoomListRes;
import com.ecommerce.api.chatroom.dto.CreateChatRoomReq;
import com.ecommerce.api.chatroom.entity.ChatRoom;
import com.ecommerce.api.chatroom.repository.ChatRoomRepository;
import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.service.ProductService;
import com.ecommerce.api.product.support.ProductImageUrlResolver;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import com.ecommerce.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.ecommerce.api.user.enums.UserRole.BUYER;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;
    private final ProductService productService;
    private final UserService userService;
    private final ProductImageUrlResolver productImageUrlResolver;

    @Transactional
    public Long create(CreateChatRoomReq req, Long userId) {
        // 기존에 유효한 채팅이 있으면 그걸 반환
        ChatRoom activeChatRoom = findActiveChatRoom(req.productId(), userId);
        if (activeChatRoom != null)
            return activeChatRoom.getId();

        Product product = productService.getProduct(req.productId());
        User user = userService.getUserNotDeleted(userId);

        return chatRoomRepository.save(new ChatRoom(product, user)).getId();
    }

    /**
     * 페이징, 최신순(last chat createdAt 순) 정렬, projection 필요
     */
    public List<ChatRoomListRes> findChatRoomList(Long userId, UserRole userRole) {
        List<ChatRoom> chatRoomList = userRole.equals(BUYER)
                ? chatRoomRepository.findByBuyerIdAndBuyerLeftAtIsNull(userId)
                : chatRoomRepository.findByProductSellerIdAndSellerLeftAtIsNull(userId);

        List<ChatRoomListRes> result = new ArrayList<>(chatRoomList.size());
        for (ChatRoom chatRoom : chatRoomList) {
            Chat lastChat = chatService.findLastChatOf(chatRoom.getId());
            User otherUser = getOtherUser(chatRoom, userRole);
            Product product = chatRoom.getProduct();


            if (product.isDeleted()) {
                result.add(ChatRoomListRes.withDeletedProduct(chatRoom, otherUser, lastChat));
            } else {
                String productThumbnailUrl = productImageUrlResolver.resolveThumbnail(product);
                result.add(ChatRoomListRes.withValidProduct(chatRoom, otherUser, lastChat, productThumbnailUrl));
            }
        }
        return result;
    }

    public ChatRoomDetailRes getChatRoomDetail(Long chatRoomId, Long userId, UserRole userRole) {
        ChatRoom chatRoom = getChatRoom(chatRoomId);
        checkUserParticipating(chatRoom, userId);

        Product product = chatRoom.getProduct();
        String productThumbnailUrl = productImageUrlResolver.resolveThumbnail(product);

        User otherUser;
        boolean otherUserLeft;
        if (userRole.equals(BUYER)) {
            otherUser = product.getSeller();
            otherUserLeft = chatRoom.getSellerLeftAt() != null;
        } else {
            otherUser = chatRoom.getBuyer();
            otherUserLeft = chatRoom.getBuyerLeftAt() != null;
        }

        return new ChatRoomDetailRes(chatRoom, otherUser, otherUserLeft, productThumbnailUrl);
    }

    public ChatRoom getChatRoom(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHATROOM_NOT_FOUND));
    }

    @Transactional
    public void leave(Long chatRoomId, Long userId, UserRole userRole) {
        ChatRoom chatRoom = getChatRoom(chatRoomId);

        checkUserParticipating(chatRoom, userId);

        if (userRole.equals(BUYER))
            chatRoom.buyerLeave();
        else
            chatRoom.sellerLeave();

        if (chatRoom.noUserRemaining())
            chatRoomRepository.delete(chatRoom);
    }

    private ChatRoom findActiveChatRoom(Long productId, Long buyerId) {
        return chatRoomRepository.findActiveByProductIdAndBuyerId(productId, buyerId);
    }

    private void checkUserParticipating(ChatRoom chatRoom, Long userId) {
        if (chatRoom.getBuyer().getId().equals(userId) && chatRoom.getBuyerLeftAt() == null)
            return;
        if (chatRoom.getProduct().getSeller().getId().equals(userId) && chatRoom.getSellerLeftAt() == null)
            return;

        throw new AppException(ErrorCode.USER_NOT_PARTICIPATING);
    }

    private User getOtherUser(ChatRoom chatRoom, UserRole userRole) {
        return userRole.equals(BUYER)
                ? chatRoom.getProduct().getSeller()
                : chatRoom.getBuyer();
    }
}
