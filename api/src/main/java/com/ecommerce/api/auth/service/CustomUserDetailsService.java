package com.ecommerce.api.auth.service;

import com.ecommerce.api.auth.domain.CustomUserDetails;
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
        User user = userService.getUserNotDeleted(email);
        return CustomUserDetails.from(user);
    }
}
