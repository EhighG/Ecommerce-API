package com.ecommerce.api.user.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.user.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JoinReq(
        @NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
        @NotBlank(message = "닉네임은 필수입니다.") @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.") String nickname,
        @NotBlank(message = "비밀번호는 필수입니다.") String password,
        @NotBlank(message = "비밀번호 확인은 필수입니다.") String passwordCheck,
        @NotNull(message = "회원 유형은 필수입니다.") UserRole role
) {
    public JoinReq {
        if (password != null && passwordCheck != null && !password.equals(passwordCheck))
            throw new AppException(ErrorCode.PASSWORD_CHECK_MISMATCH);
        if (UserRole.ADMIN.equals(role))
            throw new AppException(ErrorCode.INVALID_INPUT);
    }
}
