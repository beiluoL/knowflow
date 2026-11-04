package com.knowflow.common.error;

/** 公开错误响应的业务码、默认消息和 HTTP 状态。 */
public interface ErrorCode {
  int code();

  String message();

  int httpStatus();
}
