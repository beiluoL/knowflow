package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑评论请求（F-06）：仅允许修改正文，归属校验在 Service 层完成。
 */
@Data
public class CommentUpdateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过 1000 字")
    private String content;
}
