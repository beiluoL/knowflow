package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码生成结果。
 * <p>
 * 前端拿到 files 后弹出目录选择对话框，再逐个写入本地磁盘。
 */
@Data
public class CodeGenResultVO {

    /** 实际使用的模型名。 */
    private String model;

    /** 解析出的待落盘文件列表；为空表示模型输出中未识别到有效代码块。 */
    private List<GeneratedFileVO> files = new ArrayList<>();

    /** 模型输出中代码块以外的说明性文字，用于在对话区展示。 */
    private String explanation;

    /** 模型原始输出，便于解析失败时前端兜底展示与用户手动复制。 */
    private String rawContent;

    /** 本次生成耗时（毫秒）。 */
    private Long elapsedMs;
}
