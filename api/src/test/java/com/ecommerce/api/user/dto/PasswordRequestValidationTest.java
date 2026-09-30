package com.ecommerce.api.user.dto;

import com.ecommerce.api.user.enums.UserRole;
import com.ecommerce.api.user.support.PasswordPolicy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("가입 비밀번호가 규칙을 어기면 비밀번호 규칙 메시지로 검증에 실패한다")
    void joinReq_withWeakPassword_failsWithPolicyMessage() {
        JoinReq req = new JoinReq("buyer@e.com", "buyer", "abcdefgh", "abcdefgh", UserRole.BUYER);

        Set<ConstraintViolation<JoinReq>> violations = validator.validate(req);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(PasswordPolicy.MESSAGE);
    }

    @Test
    @DisplayName("가입 비밀번호가 규칙을 지키면 검증을 통과한다")
    void joinReq_withValidPassword_passes() {
        JoinReq req = new JoinReq("buyer@e.com", "buyer", "abcd1234", "abcd1234", UserRole.BUYER);

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("새 비밀번호만 규칙을 검사하고 기존 비밀번호는 비어 있지 않은지만 본다")
    void modifyPasswordReq_checksPolicyOnlyForNewPassword() {
        ModifyPasswordReq oldPasswordIsWeak = new ModifyPasswordReq("weak", "abcd1234");
        ModifyPasswordReq newPasswordIsWeak = new ModifyPasswordReq("abcd1234", "weak");

        assertThat(validator.validate(oldPasswordIsWeak)).isEmpty();
        assertThat(validator.validate(newPasswordIsWeak))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(PasswordPolicy.MESSAGE);
    }

    @Test
    @DisplayName("탈퇴 요청의 비밀번호는 비어 있지 않은지만 본다")
    void withdrawReq_doesNotCheckPolicy() {
        assertThat(validator.validate(new WithdrawReq("weak"))).isEmpty();
    }
}
