package com.knowflow.ai.capability;

/**
 * AI 失败的具体原因分类（Phase 3H）。
 *
 * <p>业务层与前端按此分类做差异化降级与提示，禁止再把所有异常笼统显示为“系统错误”。
 * <ul>
 *   <li>可重试（{@link #retryable()}）：限流/供应商 5xx/超时/网络抖动，执行器按有界指数退避重试；</li>
 *   <li>不可重试：鉴权失败、非法请求/响应、额度耗尽、熔断打开、用户/系统关闭——重试只会浪费额度。</li>
 * </ul>
 */
public enum AiFailureKind {

    /** 未配置 API Key（系统级与用户级均无有效配置）。 */
    UNCONFIGURED(false, "AI 服务未配置"),
    /** API Key 无效 / 被拒（401 / 403）。 */
    INVALID_KEY(false, "API Key 无效或已失效"),
    /** 请求非法（400）：通常是 prompt/参数/模型能力问题，重试无意义。 */
    INVALID_REQUEST(false, "AI 请求被供应商拒绝"),
    /** 供应商返回 200 但结构无法解析或缺字段。 */
    INVALID_RESPONSE(false, "AI 返回内容格式不正确"),
    /** 模型不支持所需能力（如 Function Calling / 视觉）。 */
    UNSUPPORTED_CAPABILITY(false, "当前模型不支持该能力"),
    /** 供应商限流（429），可按 Retry-After 有界等待后重试。 */
    RATE_LIMITED(true, "AI 服务繁忙（限流）"),
    /** 供应商服务端错误（5xx），可有限重试。 */
    PROVIDER_ERROR(true, "AI 供应商暂时异常"),
    /** 调用超时（连接或读取超时）。 */
    TIMEOUT(true, "AI 响应超时"),
    /** 网络连接失败（DNS / 连接拒绝 / 断网）。 */
    NETWORK_ERROR(true, "无法连接 AI 服务"),
    /** 余额 / 额度不足（402 或供应商明确额度错误）。 */
    QUOTA_EXHAUSTED(false, "AI 额度不足"),
    /** 系统熔断打开中：短时间失败过多，主动快速失败。 */
    CIRCUIT_OPEN(false, "AI 服务熔断中，请稍后再试"),
    /** 并发数已达上限。 */
    CONCURRENCY_LIMITED(true, "AI 并发已满，稍后自动重试"),
    /** 超出系统配置的 token / 成本预算。 */
    BUDGET_EXCEEDED(false, "AI 调用额度已达今日上限"),
    /** 用户主动关闭 AI（前端不发起请求；后端也拒绝放行 AI 增强）。 */
    USER_DISABLED(false, "你已关闭 AI 增强"),
    /** 系统级开关关闭（knowflow.ai.capability.enabled=false）。 */
    SYSTEM_DISABLED(false, "AI 增强已被系统关闭"),
    /** 请求被取消（用户离开页面 / 中断）。 */
    CANCELLED(false, "已取消"),
    /** 其它未分类异常（保守不重试，由业务层降级）。 */
    UNKNOWN(false, "AI 服务暂时不可用");

    private final boolean retryable;
    private final String userMessage;

    AiFailureKind(boolean retryable, String userMessage) {
        this.retryable = retryable;
        this.userMessage = userMessage;
    }

    public boolean retryable() {
        return retryable;
    }

    public String userMessage() {
        return userMessage;
    }
}
