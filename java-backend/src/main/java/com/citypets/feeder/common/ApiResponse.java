package com.citypets.feeder.common;

public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = "OK";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> accepted(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 202;
        r.message = "Accepted - processing asynchronously";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> rateLimited() {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 429;
        r.message = "Too Many Requests - rate limit exceeded";
        return r;
    }

    public static <T> ApiResponse<T> circuitOpen(String detail) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 503;
        r.message = "Service Unavailable - circuit breaker open: " + detail;
        return r;
    }
}
