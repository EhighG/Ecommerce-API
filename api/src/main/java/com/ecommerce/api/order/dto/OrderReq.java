package com.ecommerce.api.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderReq(
        @NotEmpty List<@NotNull Long> cartItemIdList
) {}
