package com.knowflow.exception;

import lombok.Getter;

/**
 * 业务异常：携带业务码 code，供全局异常处理器映射为规范响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
