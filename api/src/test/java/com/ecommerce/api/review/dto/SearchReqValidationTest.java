package com.ecommerce.api.review.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecommerce.api.review.dto.SearchReq.SearchBy.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

class SearchReqValidationTest {

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

    @ParameterizedTest
    @ValueSource(ints = {20, 50, 100})
    @DisplayName("리뷰 목록의 페이지 크기는 20, 50, 100을 허용한다")
    void searchReq_withAllowedSize_passes(int size) {
        assertThat(validator.validate(new SearchReq(PRODUCT, 1L, null, 0, size))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 30, 1000})
    @DisplayName("리뷰 목록의 페이지 크기가 20, 50, 100이 아니면 검증에 실패한다")
    void searchReq_withOtherSize_fails(int size) {
        assertThat(validator.validate(new SearchReq(PRODUCT, 1L, null, 0, size)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("페이지 크기는 20, 50, 100 중 하나여야 합니다.");
    }

    @Test
    @DisplayName("페이지 크기를 주지 않으면 20으로 채운다")
    void searchReq_withoutSize_defaultsTo20() {
        SearchReq req = new SearchReq(PRODUCT, 1L, null, null, null);

        assertThat(req.size()).isEqualTo(20);
        assertThat(validator.validate(req)).isEmpty();
    }
}
