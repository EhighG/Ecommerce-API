package com.ecommerce.api.common.exception;

import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

public enum ErrorCode {

    // 0xxx: Auth
    INVALID_AUTHORITIES(FORBIDDEN, 0000, "Invalid authorities"),
    NO_PERMISSIONS(NOT_FOUND, 0001, "Not found"),

    // 1xxx: User
    USER_NOT_FOUND(NOT_FOUND, 1000, "User not found"),
    EMAIL_ALREADY_EXISTS(CONFLICT, 1001, "Email already in use"),
    PASSWORD_CHECK_MISMATCH(BAD_REQUEST, 1002, "Password check mismatch"),
    WRONG_PASSWORD(UNAUTHORIZED, 1003, "Wrong password"),
    WITHDRAW_WHEN_ACTIVE_ORDER_EXISTS(FORBIDDEN, 1004, "진행중인 주문이 있어 탈퇴가 불가능합니다."),

    // 2xxx: Product & Inventory
    PRODUCT_NOT_FOUND(NOT_FOUND, 2000, "Product not found"),
    PRODUCT_CATEGORY_NOT_FOUND(BAD_REQUEST, 2001, "Product category not found"),
    SELLER_NOT_MATCHED(FORBIDDEN, 2002, "권한이 없습니다."),
    INVALID_SORT_TYPE(BAD_REQUEST, 2003, "정렬 기준값이 잘못되었습니다."),
    DELETED_PRODUCT(BAD_REQUEST, 2004, "삭제된 상품입니다."),
    PRODUCT_CATEGORY_ALREADY_EXISTS(CONFLICT, 2005, "Product category already exists"),
    PRODUCT_STAT_NOT_FOUND(INTERNAL_SERVER_ERROR, 2006, "잠시 후 다시 시도해주세요."),

    NO_INVENTORY_FOR_PRODUCT(INTERNAL_SERVER_ERROR, 2500, "해당 상품의 재고정보가 없습니다. (관리자 문의 필요)"),
    INSUFFICIENT_INVENTORY(BAD_REQUEST, 2501, "재고가 부족합니다."),

    // 3xxx: Order & OrderItem & Inventory
    ORDER_NOT_FOUND(NOT_FOUND, 3000, "주문정보를 찾을 수 없습니다"),
    ORDER_ACCESS_DENIED(ORDER_NOT_FOUND.httpStatus, 3001, ORDER_NOT_FOUND.message),
    ORDER_ITEM_NOT_FOUND(NOT_FOUND, 3002, "OrderItem not found"),
    WRONG_STATUS_CHANGE(BAD_REQUEST, 3003, "해당 처리가 불가능한 주문입니다."),

//    // 4xxx: Payment
//    PAYMENT_ALREADY_EXISTS(CONFLICT, 4001, "Payment already exists"),

//    // 5xxx: Chat & ChatRoom
//    CHATROOM_NOT_FOUND(NOT_FOUND, 5000, "Chatroom not found"),
//    USER_NOT_PARTICIPATING(FORBIDDEN, 5001, "참여중인 채팅이 아닙니다."),
//    ONLY_BUYER_START_CHATTING(FORBIDDEN, 5002, "상품 구매자만 채팅을 시작할 수 있습니다."),

    // 6xxx: Review
    REVIEW_NOT_FOUND(NOT_FOUND, 6000, "Review not found"),
    NO_CONFIRMED_ORDER_EXISTS(FORBIDDEN, 6001, "구매확정된 주문이 있는 상품에만 리뷰 작성이 가능합니다."),
    REVIEW_ALREADY_EXISTS(FORBIDDEN, 6002, "상품당 1개의 리뷰만 작성 가능합니다."),
    REVIEW_WRITER_MISMATCH(NOT_FOUND, 6003, "Review not found"),

    // 7xxx: CartItem
    CART_ITEM_NOT_FOUND(NOT_FOUND, 7000, "CartItem not found"),

    // 8xxx: Media
    MEDIA_OBJECT_NOT_FOUND(NOT_FOUND, 8000, "Media object not found"),
    INVALID_MEDIA_CONTENT_TYPE(BAD_REQUEST, 8001, "Invalid media content type"),
    IMAGE_OWNER_MISMATCH(BAD_REQUEST, 8002, "image owner mismatch"),
    UPLOADED_IMAGE_NOT_FOUND(BAD_REQUEST, 8003, "uploaded image not found"),
    IMAGE_ALREADY_ATTACHED(BAD_REQUEST, 8004, "Image already in use"),

    // 9xxx: 공통
    NOT_SUPPORTED(INTERNAL_SERVER_ERROR, 9000, "Not supported Operation"),
    INVALID_INPUT(BAD_REQUEST, 9001, "Invalid input"),
    ORDER_QUANTITY_MUST_PLUS(BAD_REQUEST, 9002, "Quantity must be greater than 0"),


    // 9999: 미식별
    UNRECOGNIZED(INTERNAL_SERVER_ERROR, 9999, "미식별 오류"),
    ;

    private HttpStatus httpStatus;
    private final int code;
    private final String message;

    ErrorCode(ErrorCode errorCode, String message) {
        this.httpStatus = errorCode.httpStatus;
        this.code = errorCode.code;
        this.message = message;
    }

    ErrorCode(HttpStatus httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
