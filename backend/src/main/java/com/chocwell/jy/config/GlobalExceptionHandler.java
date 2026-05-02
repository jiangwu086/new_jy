package com.chocwell.jy.config;

import com.chocwell.jy.util.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：将业务异常统一转为 R.fail 响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public R<?> handleIllegalArgument(IllegalArgumentException e) {
        return R.fail(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public R<?> handleIllegalState(IllegalStateException e) {
        return R.fail(e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public R<?> handleRuntime(RuntimeException e) {
        log.error("系统异常", e);
        return R.fail("系统繁忙，请稍后再试");
    }

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        log.error("未知异常", e);
        return R.fail("系统异常");
    }
}
