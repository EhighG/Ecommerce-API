package com.ecommerce.api.user.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.user.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JoinReq(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 20) String nickname,
        @NotBlank String password,
        @NotBlank String passwordCheck,
        @NotNull UserRole role
) {
    public JoinReq {
        if (!password.equals(passwordCheck))
            throw new AppException(ErrorCode.PASSWORD_CHECK_MISMATCH);
    }
}
