package com.knowflow.ai;

import com.knowflow.common.error.ResponseException;

/** 模型调用和协议解析异常，沿用 HTTP 500 与 AI 业务码。 */
public class AiException extends ResponseException {
  public static final int AI_ERROR_CODE = 5000;

  public AiException(String message) {
    this(AI_ERROR_CODE, message);
  }

  public AiException(int code, String message) {
    super(code, 500, message, null);
  }
}
