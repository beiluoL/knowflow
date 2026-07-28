package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserAiConfigDTO {
    @NotBlank(message = "模型提供商不能为空")
    private String provider;
    @NotBlank(message = "API Key 不能为空")
    private String apiKey;
    private String baseUrl;
    private String model;
    private Integer isActive;
}
