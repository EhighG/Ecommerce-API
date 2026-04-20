package com.ecommerce.api.order.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class UserSnapshot {

    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    public UserSnapshot(Long id, String nickname) {
        this.id = id;
        this.nickname = nickname;
    }
}
