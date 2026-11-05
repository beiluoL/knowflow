package com.knowflow.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.AiException;
import com.knowflow.common.PageQuery;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.error.ErrorCode;
import com.knowflow.common.error.ResponseException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证统一响应、分页、异常原因及 HTTP 错误契约。
 */
class CommonResponseContractTest {
  private record TestCode(int code, String message, int httpStatus) implements ErrorCode {}

  @Test
  void preservesJsonAndPagination() throws Exception {
    var mapper = new ObjectMapper();
    assertEquals(
        mapper.readTree("{\"code\":200,\"message\":\"ok\",\"data\":42}"),
        mapper.valueToTree(Result.success("ok", 42)));
    var query = new PageQuery();
    query.setPageNum(0);
    query.setPageSize(101);
    assertEquals(1, query.getPageNum());
    assertEquals(100, query.getPageSize());
    query.setPageSize(null);
    assertEquals(10, query.getPageSize());

    var page = new Page<Integer>(2, 10, 23);
    page.setRecords(List.of(42));
    assertEquals(
        mapper.readTree(
            "{\"records\":[42],\"total\":23,\"pageNum\":2,\"pageSize\":10,\"pages\":3}"),
        mapper.readTree(mapper.writeValueAsString(PageResult.of(page))));
  }

  @Test
  void preservesCauseAndValidatesExplicitStatus() {
    var cause = new IllegalStateException();
    var code = new TestCode(9001, "default", 409);
    assertEquals("default", new BusinessException(code).getMessage());
    var exception = new BusinessException(code, "custom", cause);
    assertSame(cause, exception.getCause());
    assertEquals(409, exception.getHttpStatus());
    assertEquals(9001, exception.getCode());
    assertThrows(
        IllegalArgumentException.class,
        () -> new BusinessException(new TestCode(9001, "invalid", 999)));
    assertEquals(500, ((ResponseException) new AiException("AI unavailable")).getHttpStatus());
  }

  @Test
  void dispatchesThroughMvcWithoutLeakingInternalMessages() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new ExceptionEndpoint())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    mvc.perform(get("/contract/ai"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(5000));
    mvc.perform(get("/contract/legacy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(500));
    mvc.perform(get("/contract/custom"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(9001))
        .andExpect(jsonPath("$.message").value("custom"));
    mvc.perform(get("/contract/unknown"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(500))
        .andExpect(jsonPath("$.message").value("系统内部错误"));
  }

  @RestController
  static class ExceptionEndpoint {
    @GetMapping("/contract/{kind}")
    void fail(@PathVariable("kind") String kind) {
      throw switch (kind) {
        case "ai" -> new AiException("AI unavailable");
        case "legacy" -> new BusinessException("legacy");
        case "custom" -> new BusinessException(new TestCode(9001, "default", 409), "custom", null);
        default -> new IllegalStateException("private connection information");
      };
    }
  }
}
