package com.ecommerce.api.user.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.common.openapi.ApiErrorCodes;
import com.ecommerce.api.user.dto.*;
import com.ecommerce.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "User", description = "가입, 프로필, 회원 정보, 탈퇴")
@RequiredArgsConstructor
@RequestMapping("/users")
@RestController
public class UserController {

    private final UserService userService;

    @Operation(summary = "가입", description = "응답은 사용자 ID다.")
    @ApiErrorCode(value = EMAIL_ALREADY_EXISTS, when = "이미 쓰는 이메일. 탈퇴한 사용자의 이메일도 포함한다")
    @PostMapping
    public ResponseEntity<Long> join(@Valid @RequestBody JoinReq req) {
        return ResponseEntity
                .ok(userService.join(req));
    }

    @Operation(summary = "사용자 프로필")
    @ApiErrorCode(value = USER_NOT_FOUND, when = "없거나 탈퇴한 사용자")
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileRes> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity
                .ok(userService.getUserProfile(userId));
    }

    @Operation(summary = "내 프로필")
    @ApiErrorCodes({})
    @GetMapping("/me")
    public ResponseEntity<UserProfileRes> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(userService.getUserProfile(userDetails.getUserId()));
    }

    @Operation(summary = "회원 목록")
    @ApiErrorCodes({})
    @GetMapping
    public ResponseEntity<List<UserInfoListRes>> getUserInfoList(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(userService.getUserInfoList(userDetails.getUserRole()));
    }

    @Operation(summary = "닉네임 변경")
    @ApiErrorCodes({})
    @PatchMapping("/me")
    public ResponseEntity<Void> modifyInfo(@Valid @RequestBody ModifyInfoReq req,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.modifyInfo(req, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "비밀번호 변경", description = "성공하면 현재 세션이 로그아웃된다.")
    @ApiErrorCode(value = WRONG_PASSWORD, when = "현재 비밀번호가 틀림")
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
    @Operation(summary = "탈퇴", description = "성공하면 현재 세션이 로그아웃된다.")
    @ApiErrorCode(value = WRONG_PASSWORD, when = "비밀번호가 틀림")
    @ApiErrorCode(value = WITHDRAW_WHEN_ACTIVE_ORDER_EXISTS, when = "판매자의 상품에 주문완료나 배송중인 주문항목이 있음")
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
