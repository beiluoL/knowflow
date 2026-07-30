package com.knowflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送私聊消息请求参数
 */
@Data
public class PrivateMessageSendDTO {

    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /** 消息类型：TEXT-文本，IMAGE-图片，FILE-文件，CODE-代码块 */
    private String messageType = "TEXT";

    @NotNull(message = "消息内容不能为空")
    private String content;

    /** 文件URL（图片/文件类型使用） */
    private String fileUrl;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 代码语言（代码块类型使用） */
    private String codeLanguage;
}
