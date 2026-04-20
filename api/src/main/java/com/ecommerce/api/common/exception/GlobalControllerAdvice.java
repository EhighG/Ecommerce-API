package com.ecommerce.api.common.exception;

import com.ecommerce.api.common.api.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static com.ecommerce.api.common.exception.ErrorCode.*;
import static com.ecommerce.api.common.exception.ErrorCode.INVALID_INPUT;

@Slf4j
@RestControllerAdvice
public class GlobalControllerAdvice {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleAppException(AppException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(new ApiError(errorCode));
    }

    // Bean Validation 실패, ModelAttribute 관련
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
    })
    public ResponseEntity<ApiError> handleValidationException(BindException e) {
        String message = extractValidationMessage(e);

        return ResponseEntity
                .badRequest()
                .body(new ApiError(INVALID_INPUT.code(), message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        log.error("요청 인자 타입 오류: ", e);
        return ResponseEntity
                .badRequest()
                .body(new ApiError(INVALID_INPUT.code(), "요청 인자 타입 오류"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        log.error("필수 요청 파라미터 누락: ", e);
        return ResponseEntity
                .badRequest()
                .body(new ApiError(INVALID_INPUT.code(), "필수 요청 파라미터 누락"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e
    ) {
        log.error("요청 Body 읽기 실패: ", e);
        return ResponseEntity
                .badRequest()
                .body(new ApiError(INVALID_INPUT.code(), "요청 Body 읽기 실패"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOtherException(Exception e) {
        // 로깅 추가
        log.error("처리되지 않은 예외: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(UNRECOGNIZED.code(), "잠시 후 다시 시도해주세요."));
    }

    private String extractValidationMessage(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null)
            return fieldError.getDefaultMessage();

        return INVALID_INPUT.message();
    }
}
