package com.ecommerce.api.common.exception;

import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

public enum ErrorCode {

    // 0xxx: Auth
    INVALID_AUTHORITIES(FORBIDDEN, "0000", "사용자 권한 정보가 올바르지 않습니다."),
    NO_PERMISSIONS(NOT_FOUND, "0001", "요청한 리소스를 찾을 수 없습니다."),

    // 1xxx: User
    USER_NOT_FOUND(NOT_FOUND, "1000", "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(CONFLICT, "1001", "이미 사용 중인 이메일입니다."),
    PASSWORD_CHECK_MISMATCH(BAD_REQUEST, "1002", "비밀번호 확인이 일치하지 않습니다."),
    WRONG_PASSWORD(UNAUTHORIZED, "1003", "비밀번호가 일치하지 않습니다."),
    WITHDRAW_WHEN_ACTIVE_ORDER_EXISTS(FORBIDDEN, "1004", "진행중인 주문이 있어 탈퇴가 불가능합니다."),

    // 2xxx: Product & Inventory
    PRODUCT_NOT_FOUND(NOT_FOUND, "2000", "상품을 찾을 수 없습니다."),
    PRODUCT_CATEGORY_NOT_FOUND(BAD_REQUEST, "2001", "상품 카테고리를 찾을 수 없습니다."),
    SELLER_NOT_MATCHED(FORBIDDEN, "2002", "해당 상품에 대한 권한이 없습니다."),
    INVALID_SORT_TYPE(BAD_REQUEST, "2003", "지원하지 않는 정렬 기준입니다."),
    DELETED_PRODUCT(BAD_REQUEST, "2004", "삭제된 상품입니다."),
    PRODUCT_CATEGORY_ALREADY_EXISTS(CONFLICT, "2005", "이미 존재하는 상품 카테고리입니다."),
    PRODUCT_STAT_NOT_FOUND(INTERNAL_SERVER_ERROR, "2006", "상품 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),

    NO_INVENTORY_FOR_PRODUCT(INTERNAL_SERVER_ERROR, "2500", "상품 재고 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    INSUFFICIENT_INVENTORY(BAD_REQUEST, "2501", "주문 가능한 재고가 부족합니다."),

    // 3xxx: Order & OrderItem & Inventory
    ORDER_NOT_FOUND(NOT_FOUND, "3000", "주문을 찾을 수 없습니다."),
    ORDER_ACCESS_DENIED(ORDER_NOT_FOUND.httpStatus, "3001", ORDER_NOT_FOUND.message),
    ORDER_ITEM_NOT_FOUND(NOT_FOUND, "3002", "주문 항목을 찾을 수 없습니다."),
    WRONG_STATUS_CHANGE(BAD_REQUEST, "3003", "현재 주문 상태에서는 처리할 수 없습니다."),
    ORDER_STATUS_CONFLICT(CONFLICT, "3004", "주문 상태가 변경되었습니다. 새로고침 후 다시 시도해주세요."),

    // 6xxx: Review
    REVIEW_NOT_FOUND(NOT_FOUND, "6000", "리뷰를 찾을 수 없습니다."),
    NO_CONFIRMED_ORDER_EXISTS(FORBIDDEN, "6001", "구매 확정한 상품에만 리뷰를 작성할 수 있습니다."),
    REVIEW_ALREADY_EXISTS(FORBIDDEN, "6002", "이미 리뷰를 작성한 상품입니다."),
    REVIEW_WRITER_MISMATCH(REVIEW_NOT_FOUND.httpStatus, "6003", REVIEW_NOT_FOUND.message),

    // 7xxx: CartItem
    CART_ITEM_NOT_FOUND(NOT_FOUND, "7000", "장바구니 항목을 찾을 수 없습니다."),

    // 75xx: Coupon
    COUPON_EVENT_NOT_FOUND(NOT_FOUND, "7500", "쿠폰 이벤트를 찾을 수 없습니다."),
    COUPON_EXPIRED(BAD_REQUEST, "7501", "사용할 수 없는 쿠폰입니다."),
    INVALID_COUPON_STATUS(BAD_REQUEST, "7502", "쿠폰 상태가 올바르지 않습니다."),
    COUPON_EVENT_CLOSED(BAD_REQUEST, "7503", "진행 중인 쿠폰 이벤트가 아닙니다."),
    COUPON_EVENT_READ_FAILED(INTERNAL_SERVER_ERROR, "7504", "쿠폰 이벤트 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."),
    COUPON_ISSUE_FAILED(INTERNAL_SERVER_ERROR, "7505", "쿠폰 발급 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    COUPON_ALREADY_ISSUED(BAD_REQUEST, "7506", "이미 발급받은 쿠폰입니다."),
    COUPON_SOLD_OUT(BAD_REQUEST, "7507", "쿠폰이 모두 소진되었습니다."),
    COUPON_EVENT_CACHING_FAILED(INTERNAL_SERVER_ERROR, "7508", "쿠폰 이벤트 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    DUPLICATED_COUPON(BAD_REQUEST, "7509", "같은 쿠폰은 중복 사용할 수 없습니다."),
    COUPON_ISSUED_NOT_FOUND(BAD_REQUEST, "7510", "해당 쿠폰을 찾을 수 없습니다."),

    // 8xxx: Media
    MEDIA_OBJECT_NOT_FOUND(NOT_FOUND, "8000", "업로드된 파일을 찾을 수 없습니다."),
    INVALID_MEDIA_CONTENT_TYPE(BAD_REQUEST, "8001", "이미지 파일만 업로드할 수 있습니다."),
    IMAGE_OWNER_MISMATCH(BAD_REQUEST, "8002", "해당 이미지에 대한 권한이 없습니다."),
    UPLOADED_IMAGE_NOT_FOUND(BAD_REQUEST, "8003", "이미지를 찾을 수 없습니다."),
    IMAGE_ALREADY_ATTACHED(BAD_REQUEST, "8004", "이미 상품에 등록된 이미지입니다."),

    // 9xxx: 공통
    NOT_SUPPORTED(INTERNAL_SERVER_ERROR, "9000", "지원하지 않는 처리입니다."),
    INVALID_INPUT(BAD_REQUEST, "9001", "요청 값이 올바르지 않습니다."),
    ORDER_QUANTITY_MUST_PLUS(BAD_REQUEST, "9002", "수량은 1개 이상이어야 합니다."),

    // 91xx: Idempotency
    IDEMPOTENCY_KEY_REQUIRED(BAD_REQUEST, "9100", "Idempotency-Key 헤더가 없습니다."),
    IDEMPOTENCY_KEY_INVALID(BAD_REQUEST, "9101", "Idempotency-Key 형식이 올바르지 않습니다."),
    IDEMPOTENCY_KEY_CONFLICT(CONFLICT, "9102", "같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다."),
    IDEMPOTENCY_REQUEST_PROCESSING(CONFLICT, "9103", "동일한 요청이 이미 처리 중입니다."),



    // 9999: 미식별
    UNRECOGNIZED(INTERNAL_SERVER_ERROR, "9999", "처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    ;

    private HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(ErrorCode errorCode, String message) {
        this.httpStatus = errorCode.httpStatus;
        this.code = errorCode.code;
        this.message = message;
    }

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
