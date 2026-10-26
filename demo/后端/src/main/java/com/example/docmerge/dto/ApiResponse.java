package com.example.docmerge.dto;

/**
 * 统一响应体。
 *
 * @param code    0 表示成功，非 0 表示失败
 * @param message 提示信息
 * @param data    业务数据
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(0, message, data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
