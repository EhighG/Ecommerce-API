package com.ecommerce.api.auth.dto;

public record LoginReq(
        String email,
        String password
) {}
