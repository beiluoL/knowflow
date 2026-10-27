package com.knowflow.coding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 代码草稿（与正式提交分离）。同用户同题唯一行，保存走 upsert。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_draft")
public class CodeDraft extends BaseEntity {

    private Long userId;

    private Long questionId;

    private String language;

    private String sourceCode;
}
