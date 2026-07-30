package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私聊消息视图对象
 */
@Data
public class PrivateMessageVO {

    private Long id;

    private Long conversationId;

    private Long senderId;

    private String senderName;

    private String senderAvatar;

    /** 消息类型：TEXT-文本，IMAGE-图片，FILE-文件，CODE-代码块 */
    private String messageType;

    private String content;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private String codeLanguage;

    private LocalDateTime createTime;

    /** 是否已撤回 */
    private Boolean recalled;

    /** 是否是当前用户发送的 */
    private Boolean isMine;

    /** 对方是否已读（仅对“我发出的消息”有意义） */
    private Boolean read;
}
