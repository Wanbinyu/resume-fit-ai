package com.wanbinyu.resumefit.common;

/** 统一响应封装；前端根据 success、message 和 data 判断请求结果。 */
public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "ok");
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, "ok");
    }
}
