package com.ecommerce.api.order.dto;

public sealed interface OrderItemSearchRes
        permits BuyerOrderItemSearchRes, SellerOrderItemSearchRes {
}
