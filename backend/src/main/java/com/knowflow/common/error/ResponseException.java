package com.knowflow.common.error;

import lombok.Getter;

/** 携带可返回给客户端的消息，内部原因仅保留在异常链。 */
@Getter
public abstract class ResponseException extends RuntimeException {
  private final Integer code;
  private final int httpStatus;

  protected ResponseException(int code, int httpStatus, String message, Throwable cause) {
    super(message, cause);
    if (httpStatus != 200 && (httpStatus < 400 || httpStatus > 599)) {
      throw new IllegalArgumentException("Invalid error HTTP status: " + httpStatus);
    }
    this.code = code;
    this.httpStatus = httpStatus;
  }
}
