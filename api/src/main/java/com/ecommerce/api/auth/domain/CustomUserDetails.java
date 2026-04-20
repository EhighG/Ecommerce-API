package com.ecommerce.api.auth.domain;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final String ROLE_PREFIX = "ROLE_";

    @Getter
    private final Long userId;

    private final String email;
    private final String passwordHash;
    private final List<GrantedAuthority> authorities;

    private CustomUserDetails(Long userId, String email, String passwordHash, List<GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.authorities = authorities;
    }

    public static CustomUserDetails from(User user) {
        String role = user.getRole().name();
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role))
        );
    }

    public UserRole getUserRole() {
        if (authorities.isEmpty())
            throw new AppException(ErrorCode.INVALID_AUTHORITIES);
        String authority = authorities.getFirst().getAuthority();
        if (authority == null || !authority.startsWith(ROLE_PREFIX))
            throw new AppException(ErrorCode.INVALID_AUTHORITIES);

        return UserRole.valueOf(authority.substring(ROLE_PREFIX.length()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    /**
     * email 기반
     * @return
     */
    @Override
    public String getUsername() {
        return email;
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
