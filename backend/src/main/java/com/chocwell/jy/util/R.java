package com.chocwell.jy.util;

import lombok.Data;

/**
 * 统一 API 响应体
 * { "code": 200, "msg": "ok", "data": {...} }
 */
@Data
public class R<T> {

    private int code;
    private String msg;
    private T data;

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg  = msg;
        this.data = data;
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "ok", data);
    }

    public static <T> R<T> ok() {
        return new R<>(200, "ok", null);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(400, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }
}
