package com.ecommerce.api.user.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "abcd1234",     // 영문 + 숫자, 8자
            "ABCD!@#$",     // 영문 + 특수문자
            "1234!@#$",     // 숫자 + 특수문자
            "aB3!aB3!aB3!", // 세 종류, 12자
            "Abcdefg1"      // 대소문자는 한 종류로 센다
    })
    @DisplayName("8~12자이고 허용 문자 중 2종류 이상이면 통과한다")
    void isSatisfied_withValidPassword_returnsTrue(String password) {
        assertThat(PasswordPolicy.isSatisfied(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc1234",       // 7자
            "abcd12345678x", // 13자
            "Abcdefgh",      // 영문 한 종류(대소문자 섞어도 한 종류)
            "12345678",      // 숫자 한 종류
            "abcd 1234",     // 공백은 허용하지 않는다
            "abcd1234(",     // 허용하지 않는 특수문자
            "비밀번호12ab"    // 영문·숫자·특수문자 밖의 문자
    })
    @DisplayName("길이나 문자 종류 규칙을 어기면 통과하지 못한다")
    void isSatisfied_withInvalidPassword_returnsFalse(String password) {
        assertThat(PasswordPolicy.isSatisfied(password)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("비어 있는 값은 필수 검사에 맡기고 통과시킨다")
    void isSatisfied_withBlankPassword_returnsTrue(String password) {
        assertThat(PasswordPolicy.isSatisfied(password)).isTrue();
    }
}
