package com.ecommerce.api.user.dto;

import com.ecommerce.api.user.support.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record ModifyPasswordReq(
        @NotBlank(message = "현재 비밀번호는 필수입니다.") String oldPassword,
        @Schema(description = PasswordPolicy.MESSAGE) @NotBlank(message = "새 비밀번호는 필수입니다.") String newPassword
) {
    @Schema(hidden = true)
    @AssertTrue(message = PasswordPolicy.MESSAGE)
    public boolean isNewPasswordPolicySatisfied() {
        return PasswordPolicy.isSatisfied(newPassword);
    }
}
