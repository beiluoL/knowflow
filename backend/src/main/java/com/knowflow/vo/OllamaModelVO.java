package com.knowflow.vo;

import lombok.Data;

/**
 * Ollama 本地模型信息（对应 /api/tags 返回的模型项）。
 */
@Data
public class OllamaModelVO {

    /** 模型名（含 tag，如 llama3.1:8b） */
    private String name;

    /** 模型摘要（digest） */
    private String digest;

    /** 模型文件大小（字节） */
    private Long size;

    /** 量化格式（如 gguf） */
    private String format;

    /** 模型家族（如 llama、qwen） */
    private String family;

    /** 参数规模（如 8B、13B） */
    private String parameterSize;

    /** 量化级别（如 Q4_0、Q8_0） */
    private String quantizationLevel;

    /** 最后修改时间（ISO 字符串） */
    private String modifiedAt;

    /** 人类可读的大小（如 4.7 GB） */
    private String sizeReadable;
}
