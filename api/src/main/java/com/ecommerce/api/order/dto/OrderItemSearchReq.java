package com.ecommerce.api.order.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

import java.util.List;

public record OrderItemSearchReq(
        @Schema(description = "이 주문의 항목을 조회한다(구매자). `sellerId`와 함께 줄 수 없다") Long orderId,
        @Schema(description = "본인 ID. 자기 상품의 주문항목을 조회한다(판매자)") Long sellerId,
        @Schema(description = "`sellerId`로 조회할 때만 쓴다") List<OrderStatus> statusList,
        @Schema(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,
        @Schema(defaultValue = "20", allowableValues = {"20", "50", "100"}) @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") Integer size
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
