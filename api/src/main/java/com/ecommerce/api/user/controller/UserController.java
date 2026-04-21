package com.ecommerce.api.user.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.user.dto.*;
import com.ecommerce.api.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/users")
@RestController
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<Long> join(@Valid @RequestBody JoinReq req) {
        return ResponseEntity
                .ok(userService.join(req));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileRes> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity
                .ok(userService.getUserProfile(userId));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileRes> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(userService.getUserProfile(userDetails.getUserId()));
    }

    @GetMapping
    public ResponseEntity<List<UserInfoListRes>> getUserInfoList(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(userService.getUserInfoList(userDetails.getUserRole()));
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> modifyInfo(@Valid @RequestBody ModifyInfoReq req,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.modifyInfo(req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> modifyPassword(@Valid @RequestBody ModifyPasswordReq req,
                                               @AuthenticationPrincipal CustomUserDetails userDetails,
                                               HttpServletRequest request, HttpServletResponse response) {
        userService.modifyPassword(req, userDetails.getUserId());
        // 로그아웃
        new SecurityContextLogoutHandler().logout(request, response, null);

        return ResponseEntity.ok().build();
    }

//    @DeleteMapping("/me")
    @PostMapping("/me/withdraw")
    public ResponseEntity<Void> withdraw(@Valid @RequestBody WithdrawReq req,
                                         @AuthenticationPrincipal CustomUserDetails userDetails,
                                         HttpServletRequest request, HttpServletResponse response) {
        userService.withdraw(req, userDetails.getUserId());
        // 로그아웃
        new SecurityContextLogoutHandler().logout(request, response, null);

        return ResponseEntity.ok().build();
    }
}
