package com.knowflow.vo;

import lombok.Data;

@Data
public class UserAiConfigVO {
    private Long id;
    private String provider;
    /** API Key 脱敏显示（仅保留前4后4位） */
    private String apiKeyMasked;
    private String baseUrl;
    private String model;
    private Integer isActive;
}
