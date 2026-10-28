package com.wanbinyu.resumefit.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        // JSON 解析、类型转换错误可能包含原始输入或实现类名，不直接回显底层异常。
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "请求格式或参数不正确，请检查后重试"));
    }

    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.dao.ConcurrencyFailureException.class})
    ResponseEntity<ApiResponse<Void>> handleConflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(false, null, "数据已变化或存在冲突，请刷新后重试"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiResponse<>(false, null, "文件不能超过 5MB，请选择较小的简历或照片文件"));
    }
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnknown(Exception exception) {
        // 保留 Spring 的其他明确 4xx（如 405、415），避免笼统转换为服务端故障。
        if (exception instanceof org.springframework.web.ErrorResponse error && error.getStatusCode().is4xxClientError())
            return ResponseEntity.status(error.getStatusCode())
                    .body(new ApiResponse<>(false, null, "请求无法处理，请检查请求方式和内容格式"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, null, "服务暂时不可用，请稍后重试"));
    }
}
