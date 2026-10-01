package com.ecommerce.api.review.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rating {

    // 요청·응답 DTO의 halfStars 필드 설명(API 명세)
    public static final String HALF_STARS_DESCRIPTION = "0.5점 단위 별점을 정수로 나타낸 값(0~10). 화면 별점은 halfStars ÷ 2다.";

    @Min(0)
    @Max(10)
    @Column(name = "rating", nullable = false)
    private int halfStars;

    public Rating(int halfStars) {
        if (halfStars < 0 || halfStars > 10)
            throw new AppException(ErrorCode.INVALID_INPUT, "별점은 0점 이상 5점 이하로 입력해야 합니다.");
        this.halfStars = halfStars;
    }

    public BigDecimal toRating() {
        return BigDecimal.valueOf(halfStars).divide(BigDecimal.valueOf(2));
    }
}
