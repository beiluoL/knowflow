package com.knowflow.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 意图识别结果：模型对「当前输入 + 历史上下文」的结构化理解，用于前端分流与显式确认。
 */
@Data
public class AgentIntentVO {
    /** generate / modify / explain / debug / chat */
    private String intent;
    /** 识别置信度 0~1 */
    private Double confidence;
    /** 抽取的结构化参数（语言/产物/是否保存目录等） */
    private Map<String, String> slots;
    /** 是否需要向用户澄清（模糊指令） */
    private Boolean needsClarify;
    /** 澄清问题（needsClarify=true 时给出 1~3 个可选项） */
    private List<ClarifyQuestion> clarifications;
    /** 结构探针 + 语义层面的歧义点 */
    private List<AmbiguityVO> ambiguities;

    @Data
    public static class ClarifyQuestion {
        private String field;
        private String question;
        private List<String> options;
    }
}
