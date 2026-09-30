package com.ecommerce.api.auth.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    UserService userService;

    @InjectMocks
    CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("없거나 탈퇴한 사용자의 이메일이면 UsernameNotFoundException을 던진다")
    void loadUserByUsername_withUnknownEmail_throwsUsernameNotFound() {
        when(userService.getUserNotDeleted("none@e.com")).thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("none@e.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("없는 이메일로 로그인하면 내부 오류가 아니라 일반 인증 실패가 된다")
    void authenticate_withUnknownEmail_failsAsBadCredentials() {
        when(userService.getUserNotDeleted("none@e.com")).thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(new BCryptPasswordEncoder());

        // UsernameNotFoundException일 때만 Spring Security가 비밀번호 비교 시간을 맞추고 BadCredentials로 바꾼다
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("none@e.com", "abcd1234")))
                .isExactlyInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("사용자 없음이 아닌 오류는 그대로 전달한다")
    void loadUserByUsername_withOtherError_rethrows() {
        AppException other = new AppException(ErrorCode.INVALID_INPUT);
        when(userService.getUserNotDeleted("user@e.com")).thenThrow(other);

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("user@e.com"))
                .isSameAs(other);
    }
}
