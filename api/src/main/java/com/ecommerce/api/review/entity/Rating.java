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
