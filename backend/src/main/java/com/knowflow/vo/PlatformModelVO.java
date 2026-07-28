package com.knowflow.vo;

import lombok.Data;

@Data
public class PlatformModelVO {
    private String provider;
    private String label;
    private String baseUrl;
    private String model;
    private boolean subscriptionRequired;
    private String priceInfo;
}
