package com.knowflow.exception;

import com.knowflow.common.error.ErrorCode;
import com.knowflow.common.error.ResponseException;

/** 保留整数业务码的旧协议，同时支持显式 HTTP 状态。 */
public class BusinessException extends ResponseException {
  public BusinessException(String message) {
    this(500, message);
  }

  public BusinessException(Integer code, String message) {
    this(code, message, null);
  }

  public BusinessException(Integer code, String message, Throwable cause) {
    super(code == null ? 500 : code, legacyHttpStatus(code), message, cause);
  }

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.message(), null);
  }

  public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode.code(), errorCode.httpStatus(), message, cause);
  }

  private static int legacyHttpStatus(Integer code) {
    return code != null && code >= 400 && code < 500 ? code : 200;
  }
}
