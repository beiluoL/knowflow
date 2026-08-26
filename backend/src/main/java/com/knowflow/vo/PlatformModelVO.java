package com.knowflow.vo;

import lombok.Data;

@Data
/**
 * 平台预设模型信息，由 {@link com.knowflow.config.AiProviderRegistry} 统一派生。
 * 含云端 11 个 + 本地 4 类，前端据此渲染模型选择列表。
 */
public class PlatformModelVO {
    private String provider;
    private String label;
    private String baseUrl;
    private String model;
    private boolean subscriptionRequired;
    private String priceInfo;
    private String providerType;
    private String capability;
    private String defaultModel;
    /** 官网地址 */
    private String websiteUrl;
    /** API Key 获取引导步骤 */
    private String[] keyGuide;
    /** 推荐模型列表 */
    private String[] popularModels;
}
