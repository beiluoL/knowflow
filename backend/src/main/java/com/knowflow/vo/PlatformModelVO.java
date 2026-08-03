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
    /** CLOUD / LOCAL。 */
    private String providerType;
    /** LIGHT / STANDARD / POWERFUL。 */
    private String capability;
    /** 默认推荐模型。 */
    private String defaultModel;
}
