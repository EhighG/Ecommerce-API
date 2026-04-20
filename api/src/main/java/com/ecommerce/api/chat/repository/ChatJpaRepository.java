package com.ecommerce.api.chat.repository;

import com.ecommerce.api.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatJpaRepository extends ChatRepository, JpaRepository<Chat, Long> {
}
