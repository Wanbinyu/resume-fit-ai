package com.wanbinyu.resumefit.common;

public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "ok");
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, "ok");
    }
}
