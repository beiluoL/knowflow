package com.knowflow.dto;

import lombok.Data;

/**
 * 间隔重复卡片（知识复习）新增/编辑入参。
 */
@Data
public class WbReviewCardDTO {

    /** 来源收集箱条目ID */
    private Long captureId;

    /** 来源康奈尔笔记ID */
    private Long noteId;

    /** 归属知识库/分类ID */
    private Long categoryId;

    /** 卡片正面：问题/线索 */
    private String front;

    /** 卡片背面：答案/解释 */
    private String back;

    /** 卡片类型：BASIC/CLOZE/RECALL */
    private String cardType;
}
