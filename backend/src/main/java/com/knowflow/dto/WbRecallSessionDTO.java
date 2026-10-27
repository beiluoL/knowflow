package com.knowflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 主动回忆会话 DTO：支持创建、编辑、保存草稿、提交单轮默写、确认遗漏与归档。
 * 字段按需填写：创建时填 sourceText/title/noteId/sourceType/draft，
 * 提交某轮时填 round + text，保存草稿时填 round + text，确认遗漏时填 round + gaps。
 */
@Data
public class WbRecallSessionDTO {

    /** 创建时是否保存为草稿（true 不启动第一轮） */
    private Boolean draft;

    /** 原文来源类型：PASTE 粘贴 / NOTE 从笔记选择 */
    private String sourceType;

    /** 归档标记：0 未归档 / 1 已归档 */
    private Integer archived;

    /** 用户确认的关键遗漏（仅该轮生效） */
    private List<String> gaps;

    /** 关联笔记ID */
    private Long noteId;

    /** 关联复习卡ID */
    private Long cardId;

    /** 标题 */
    private String title;

    /** 原文（默写对照基准） */
    private String sourceText;

    /** 当前提交的轮次：1 / 2 / 3 */
    private Integer round;

    /** 本轮默写内容 */
    private String text;
}
