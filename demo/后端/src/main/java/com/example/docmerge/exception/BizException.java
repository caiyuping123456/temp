package com.example.docmerge.exception;

import org.springframework.http.HttpStatus;

/**
 * 可预期的业务异常，由全局异常处理器转换为统一 JSON。
 */
public class BizException extends RuntimeException {

    private final HttpStatus status;
    private final int code;

    public BizException(HttpStatus status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BizException(HttpStatus status, String message) {
        this(status, status.value(), message);
    }

    public static BizException badRequest(String message) {
        return new BizException(HttpStatus.BAD_REQUEST, 40001, message);
    }

    public static BizException unsupportedMedia(String message) {
        return new BizException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 41500, message);
    }

    public static BizException notFound(String message) {
        return new BizException(HttpStatus.NOT_FOUND, 40400, message);
    }

    public static BizException internal(String message) {
        return new BizException(HttpStatus.INTERNAL_SERVER_ERROR, 50000, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }
}
