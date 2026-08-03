package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 本地代码生成请求（Ollama + deepseek-coder）。
 * <p>
 * 与 {@link CodeAgentChatDTO} 的区别：本 DTO 面向「一句自然语言指令 → 一组可落盘的代码文件」场景，
 * 不承载多轮对话历史，后端会用固定的产物约束提示词包裹用户指令，要求模型输出结构化代码块。
 */
@Data
public class CodeGenDTO {

    /** 用户的自然语言指令，例如「替我写一个 html demo 案例」。 */
    @NotBlank(message = "指令内容不能为空")
    @Size(max = 2000, message = "指令内容不能超过 2000 字")
    private String prompt;

    /** 可选：Ollama 服务地址；为空时回退到当前用户的 Ollama 配置。 */
    private String baseUrl;

    /** 可选：生成使用的模型名；为空时使用默认的 deepseek-coder:6.7b。 */
    private String model;

    /** 可选：采样温度（0~2）。代码生成默认使用较低温度以保证稳定性。 */
    private Double temperature;
}
