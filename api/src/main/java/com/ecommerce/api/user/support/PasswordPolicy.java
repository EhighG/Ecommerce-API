package com.ecommerce.api.user.support;

/**
 * 가입 비밀번호와 변경할 새 비밀번호의 규칙(docs/security.md "비밀번호 규칙").
 * 12자 이하의 ASCII만 허용하므로 BCrypt 입력 한도(72바이트)도 넘지 않는다.
 */
public final class PasswordPolicy {

    public static final String MESSAGE = "비밀번호는 8~12자이고, 영문·숫자·특수문자(!@#$%^&*) 중 2종류 이상을 써야 합니다.";

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 12;
    private static final String SPECIAL_CHARACTERS = "!@#$%^&*";

    private PasswordPolicy() {
    }

    /**
     * 비어 있는 값은 통과시킨다. 필수 여부는 {@code @NotBlank}가 검사한다.
     */
    public static boolean isSatisfied(String password) {
        if (password == null || password.isBlank())
            return true;
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH)
            return false;

        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
                hasLetter = true;
            else if (c >= '0' && c <= '9')
                hasDigit = true;
            else if (SPECIAL_CHARACTERS.indexOf(c) >= 0)
                hasSpecial = true;
            else
                return false;
        }

        int kinds = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        return kinds >= 2;
    }
}
