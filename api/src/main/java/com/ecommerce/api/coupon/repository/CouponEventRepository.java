package com.ecommerce.api.coupon.repository;

import com.ecommerce.api.coupon.entity.CouponEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponEventRepository extends JpaRepository<CouponEvent, Long> {
}
