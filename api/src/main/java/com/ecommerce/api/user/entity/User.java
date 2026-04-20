package com.ecommerce.api.user.entity;

import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.user.enums.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@SQLDelete(sql = "update users set deleted = true where id = ?")
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        }
)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String email;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Column(nullable = false)
    private boolean deleted = false;

    private User(String email, String nickname, String password, UserRole role) {
        this.email = email;
        this.nickname = nickname;
        this.password = password;
        this.role = role;
    }

    public static User join(String email, String nickname, String encodedPassword, UserRole role) {
        return new User(email, nickname, encodedPassword, role);
    }

    public boolean withdraw() {
        if (deleted)
            return false;
        deleted = true;
        return true;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
