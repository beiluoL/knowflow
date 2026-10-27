package com.knowflow.ai.capability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 能力层配置（{@code knowflow.ai.capability.*}）。
 *
 * <p>默认值面向单机教学环境：保守重试、快速熔断、不开启付费健康检查。
 * 所有参数均可通过环境变量覆盖，不需要改代码即可调参。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "knowflow.ai.capability")
public class AiCapabilityProperties {

    /** 系统级 AI 总开关；关闭后所有 AI 增强快速失败，核心学习流程不受影响。 */
    private volatile boolean enabled = true; // 开关可能在配置刷新线程更新，调用线程必须读取最新值。

    /** 单次请求最大尝试次数（含首次）；仅对可重试错误生效。401 等不可重试错误只尝试 1 次。 */
    private int maxAttempts = 3;

    /** 首次重试退避基数（毫秒），之后指数增长并加抖动。 */
    private long initialBackoffMs = 500;

    /** 单次退避上限（毫秒），防止指数增长把请求拖得过久。 */
    private long maxBackoffMs = 8000;

    /** 全局 AI 并发上限（信号量）；获取不到准入名额时按可重试失败处理。 */
    private int maxConcurrent = 8;

    /** 获取并发准入名额的等待时间（毫秒），超时即 CONCURRENCY_LIMITED。 */
    private long acquireTimeoutMs = 100;

    /** 熔断：滑动统计窗口（毫秒）。 */
    private long circuitWindowMs = 60_000;

    /** 熔断：窗口内失败次数达到该阈值即打开熔断。 */
    private int circuitFailureThreshold = 6;

    /** 熔断：打开后保持时长（毫秒），到期进入半开（放一次探测）。 */
    private int circuitOpenMs = 30_000;

    /** 每日 token 预算（输入+输出）；0 表示不限制。内存计数，进程重启归零，仅作护栏而非计费依据。 */
    private long dailyTokenLimit = 0;

    /** 能力状态接口缓存时间（秒）；状态由配置 + 近期调用结果聚合，不主动打付费健康检查。 */
    private int statusCacheSeconds = 30;

    /** 限流/超时等瞬时状态在聚合状态中的冷却保留时间（秒）。 */
    private int transientStateTtlSeconds = 60;
}
