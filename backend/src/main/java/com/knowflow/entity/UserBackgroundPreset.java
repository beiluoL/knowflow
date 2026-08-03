package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户自定义背景预设
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_background_preset")
public class UserBackgroundPreset extends BaseEntity {

    private Long userId;

    /** 预设名称 */
    private String name;

    /** 背景类型：color / gradient / preset */
    private String bgType;

    /** 背景值：纯色为 #hex，渐变为 linear-gradient(...)，预设为预设 ID */
    private String bgValue;

    /** 缩略图 CSS 值（用于前端预览展示） */
    private String thumbnail;
}
