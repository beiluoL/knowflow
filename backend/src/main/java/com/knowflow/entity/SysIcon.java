package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义图标实体。
 * type: custom(文件上传) / iconfont(Unicode code) / svg(SVG 代码)
 * content: custom→base64 data URI; iconfont→Unicode 码点(如 e601); svg→SVG 代码
 * color: 图标颜色(如 #3B6FE0)，为空则继承当前文字颜色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_icon")
public class SysIcon extends BaseEntity {
    private String name;
    private String type;
    private String content;
    private String color;
    private Long userId;
}
