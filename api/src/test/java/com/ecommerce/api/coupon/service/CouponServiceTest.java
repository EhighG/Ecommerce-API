package com.ecommerce.api.coupon.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.coupon.entity.CouponEvent;
import com.ecommerce.api.coupon.entity.CouponIssued;
import com.ecommerce.api.coupon.enums.CouponStatus;
import com.ecommerce.api.coupon.enums.CouponType;
import com.ecommerce.api.coupon.repository.CouponIssuedRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.ecommerce.api.support.UnitTestFixtures.buyer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    CouponIssuedRepository couponIssuedRepository;

    @InjectMocks
    CouponService couponService;

    @Test
    @DisplayName("복구할 발급 쿠폰이 없으면 쿠폰 복구 실패 오류(500)를 반환한다")
    void restoreCouponIssued_whenCouponIssuedNotFound_throwsCouponRestoreFailed() {
        // given
        Long couponIssuedId = 1L;
        when(couponIssuedRepository.findById(couponIssuedId)).thenReturn(Optional.empty());

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> couponService.restoreCouponIssued(couponIssuedId, Instant.now()))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.COUPON_RESTORE_FAILED);
    }

    @Test
    @DisplayName("복구할 발급 쿠폰이 사용 상태가 아니면 상태를 바꾸지 않고 쿠폰 복구 실패 오류(500)를 반환한다")
    void restoreCouponIssued_whenCouponIssuedNotUsed_throwsCouponRestoreFailed() {
        // given
        Long couponIssuedId = 1L;
        CouponIssued couponIssued = issuedCoupon();
        when(couponIssuedRepository.findById(couponIssuedId)).thenReturn(Optional.of(couponIssued));

        // when & then
        assertThatExceptionOfType(AppException.class)
                .isThrownBy(() -> couponService.restoreCouponIssued(couponIssuedId, Instant.now()))
                .extracting(AppException::getErrorCode)
                .isEqualTo(ErrorCode.COUPON_RESTORE_FAILED);

        assertThat(couponIssued.getStatus()).isEqualTo(CouponStatus.ISSUED);
    }

    private CouponIssued issuedCoupon() {
        Instant now = Instant.now();
        CouponEvent couponEvent = new CouponEvent(
                "coupon",
                CouponType.FIXED_AMOUNT,
                1_000L,
                1_000L,
                100,
                now.minusSeconds(60),
                now.plusSeconds(3_600),
                3_600
        );
        return new CouponIssued(couponEvent, buyer(), now);
    }
}
