package com.ecommerce.api.order.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.order.enums.OrderStatus;
import jakarta.validation.constraints.Min;

import java.util.List;

public record OrderItemSearchReq(
        Long orderId,
        Long sellerId,
        List<OrderStatus> statusList,
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") Integer size
) {
    public OrderItemSearchReq {
        boolean ofBuyer = orderId != null && sellerId == null;
        boolean ofSeller = orderId == null && sellerId != null;
        boolean hasStatusCondition = statusList != null && !statusList.isEmpty();

        if (!ofBuyer && !ofSeller)
            throw new AppException(ErrorCode.INVALID_INPUT);
        if (ofBuyer && hasStatusCondition)
            throw new AppException(ErrorCode.INVALID_INPUT);

        if (page == null) page = 0;
        if (size == null) size = 20;
        if (!(size == 20 || size == 50 || size == 100))
            throw new AppException(ErrorCode.INVALID_INPUT, "페이지 크기는 20, 50, 100 중 하나여야 합니다.");
    }

    public boolean ofBuyer() {
        return orderId != null && sellerId == null;
    }

    public boolean ofSeller() {
        return orderId == null && sellerId != null;
    }

    public boolean hasStatusCondition() {
        return statusList != null && !statusList.isEmpty();
    }
}
