package com.knowflow.ai;

/**
 * AI 适配层统一异常：模型调用、鉴权、协议解析等失败均抛出此异常，
 * 由全局异常处理器映射为统一响应（业务码 5000 段）。
 */
public class AiException extends RuntimeException {

    /** AI 服务相关错误业务码，便于前端/监控识别。 */
    public static final int AI_ERROR_CODE = 5000;

    private final int code;

    public AiException(String message) {
        super(message);
        this.code = AI_ERROR_CODE;
    }

    public AiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
