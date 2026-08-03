package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Ollama 本地模型配置实体。
 * <p>
 * 每个用户一条记录，持久化 Ollama 服务地址、默认模型及运行时参数预设。
 * 支持导入导出（前端导出为 JSON 文件）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ollama_config")
public class OllamaConfig extends BaseEntity {

    /** 所属用户 ID */
    private Long userId;

    /** Ollama 服务地址（含协议和端口），默认 http://localhost:11434 */
    private String baseUrl;

    /** 默认使用的模型名（如 llama3.1:8b） */
    private String defaultModel;

    /** 温度参数预设（0~2） */
    private Double temperature;

    /** Top-P 核采样预设（0~1） */
    private Double topP;

    /** 最大 Token 数预设 */
    private Integer maxTokens;

    /** 连接超时（秒） */
    private Integer timeoutSeconds;
}
