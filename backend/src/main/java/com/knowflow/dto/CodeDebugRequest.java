package com.knowflow.dto;

import lombok.Data;

/**
 * 代码调试运行请求（轻量在线调试器，推进 2.1）。
 * 提供逐行执行轨迹（Python）与错误行号精确定位（全语言）。
 */
@Data
public class CodeDebugRequest {
    private String language;
    private String code;
    private Long timeLimitMs;
}
