package com.knowflow.ai;

import java.net.http.HttpTimeoutException;
import org.springframework.web.client.RestClientResponseException;

/** 把上游失败转为可公开的信息，避免响应正文、URL 或凭证进入日志和 API。 */
final class AiTransportErrors {
  private AiTransportErrors() {}

  static AiException from(String operation, Exception failure) {
    if (failure instanceof RestClientResponseException response) {
      return new AiException(operation + "：模型返回状态码 " + response.getStatusCode().value());
    }
    if (failure instanceof HttpTimeoutException
        || failure.getCause() instanceof java.net.SocketTimeoutException) {
      return new AiException(operation + "：模型请求超时");
    }
    return new AiException(operation);
  }
}
