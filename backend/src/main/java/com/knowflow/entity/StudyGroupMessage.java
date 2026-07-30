package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习小组消息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_group_message")
public class StudyGroupMessage extends BaseEntity {

    /** 小组ID */
    private Long groupId;

    /** 发送者ID */
    private Long senderId;

    /** 消息类型：TEXT-文本，IMAGE-图片，FILE-文件，CODE-代码块 */
    private String messageType;

    /** 消息内容 */
    private String content;

    /** 文件URL（图片/文件类型使用） */
    private String fileUrl;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 代码语言（代码块类型使用） */
    private String codeLanguage;

    /** @提及的用户ID列表，逗号分隔 */
    private String mentionUserIds;

    /** 是否已撤回 */
    private Integer recalled;
}