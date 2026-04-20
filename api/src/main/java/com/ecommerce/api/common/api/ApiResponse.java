//package com.ecommerce.common.api;
//
//import com.fasterxml.jackson.annotation.JsonInclude;
//
//@JsonInclude(JsonInclude.Include.NON_NULL)
//public record ApiResponse<T>(
//        T data,
//        ApiError error
//) {
//    public static <T> ApiResponse<T> success(T data) {
//        return new ApiResponse<>(data, null);
//    }
//
//    public static <T> ApiResponse<T> success() {
//        return new ApiResponse<>(null, null);
//    }
//
//    public static ApiResponse<Void> error(ApiError error) {
//        return new ApiResponse<>(null, error);
//    }
//}
