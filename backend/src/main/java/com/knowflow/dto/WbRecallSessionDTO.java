package com.knowflow.dto;

import lombok.Data;

/**
 * 主动回忆会话提交 DTO：支持创建会话、提交单轮默写。
 * 字段按需填写：创建时填 sourceText/title/noteId/cardId，
 * 提交某轮时填 round + text。
 */
@Data
public class WbRecallSessionDTO {

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
