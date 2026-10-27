package com.wanbinyu.resumefit.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

/** 将业务异常、参数校验异常和未预期错误转换为统一的 API 错误响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            new ApiResponse<>(false, null, "文件不能超过 5MB，请选择较小的简历文件")
        );
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            new ApiResponse<>(false, null, "请求的资源不存在")
        );
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidParameter() {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "请求参数格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnknown(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            new ApiResponse<>(false, null, "服务暂时不可用，请稍后重试")
        );
    }
}
