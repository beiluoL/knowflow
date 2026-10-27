package com.knowflow.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-B：协议层异常语义修复的集成测试（MockMvc 打真实端点，不是 mock 控制器）。
 *
 * <p>修复前：以下 4 类协议异常未在 {@link GlobalExceptionHandler} 中映射，全部落进兜底
 * {@code @ExceptionHandler(Exception.class)} → HTTP 500「系统内部错误」。前端
 * {@code utils/error-message.ts} 把 5xx 统一归类为 {@code server} → 用户看到
 * 「服务暂时不可用 / 稍后重试」，即**用户自己的请求错误被说成服务端故障**。</p>
 *
 * <p>修复后：分别映射为 400 / 400 / 415 / 405；已正确处理的
 * {@code MethodArgumentTypeMismatchException} → 400 行为保持不变（回归用例）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
// 独立内存库名：本项目 H2 url 固定 mem:knowflow + DB_CLOSE_DELAY=-1（跨 Spring 上下文共享同库），
// 新增上下文会重跑 schema 导致 CREATE INDEX 重复而失败。本类给自身分配独立库，避免污染共享库。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1b_protocol;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class ProtocolExceptionMappingTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 场景 1：缺少必需 query 参数。
     * <p>{@code /api/learning/certificates/verify} 声明 {@code @RequestParam String certNo}，
     * 传错参数名（code）即等价于未传 → MissingServletRequestParameterException。</p>
     */
    @Test
    @DisplayName("P1-B1: 缺少必需 query 参数 → 400（原为 500）")
    void missingRequiredRequestParamReturns400() throws Exception {
        mockMvc.perform(get("/api/learning/certificates/verify").param("code", "ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必需的请求参数：certNo"));
    }

    /** 场景 2：请求体不是合法 JSON → HttpMessageNotReadableException → 400（原为 500）。 */
    @Test
    @DisplayName("P1-B2: 请求体 JSON 畸形 → 400（原为 500）")
    void malformedJsonBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不正确"));
    }

    /** 场景 3：Content-Type 不受支持 → HttpMediaTypeNotSupportedException → 415（原为 500）。 */
    @Test
    @DisplayName("P1-B3: 不支持的 Content-Type → 415（原为 500）")
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(415))
                .andExpect(jsonPath("$.message").value("请求内容类型不受支持"));
    }

    /** 场景 4：HTTP 方法不受支持（用 GET 调仅 POST 的接口）→ 405（原为 500）。 */
    @Test
    @DisplayName("P1-B4: 不支持的 HTTP 方法 → 405（原为 500）")
    void unsupportedHttpMethodReturns405() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("请求方法不受支持"));
    }

    /**
     * 场景 5（回归）：路径变量类型不匹配 → 400。
     * <p>修复前已正确处理（F-14），本轮不得回退。</p>
     */
    @Test
    @DisplayName("P1-B5 回归: 路径变量类型不匹配 → 400（保持原行为）")
    void pathVariableTypeMismatchStillReturns400() throws Exception {
        mockMvc.perform(get("/api/docs/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数格式不正确"));
    }

    /**
     * 场景 6（对照）：业务异常仍走原语义，不受本轮改动影响。
     * <p>证书核验传对参数但查不到 → 404 + 业务提示，证明「业务校验链路」与「协议层映射」互不干扰。</p>
     */
    @Test
    @DisplayName("P1-B6 对照: 协议修复不影响业务异常语义（404 证书不存在）")
    void businessExceptionSemanticsUnchanged() throws Exception {
        mockMvc.perform(get("/api/learning/certificates/verify").param("certNo", "NO-SUCH-CERT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
