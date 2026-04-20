package com.ecommerce.api.chat.entity;

import com.ecommerce.api.chatroom.entity.ChatRoom;
import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Chat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Lob
    @Size(max = 1000)
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean deleted = false;

    public Chat(ChatRoom chatRoom, User sender, String content) {
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.content = content;
    }

    public void delete() {
        this.deleted = true;
    }
}
