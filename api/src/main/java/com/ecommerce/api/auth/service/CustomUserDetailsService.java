package com.ecommerce.api.auth.service;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user;
        try {
            user = userService.getUserNotDeleted(email);
        } catch (AppException e) {
            if (e.getErrorCode() != ErrorCode.USER_NOT_FOUND)
                throw e;
            // UsernameNotFoundException이어야 Spring Security가 없는 계정에도 비밀번호 비교 시간을 맞추고, 내부 오류 로그를 남기지 않는다
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.", e);
        }
        return CustomUserDetails.from(user);
    }
}
