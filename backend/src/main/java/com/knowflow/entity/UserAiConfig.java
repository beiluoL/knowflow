package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_ai_config")
public class UserAiConfig extends BaseEntity {
    private Long userId;
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;
    private Integer isActive;
}
