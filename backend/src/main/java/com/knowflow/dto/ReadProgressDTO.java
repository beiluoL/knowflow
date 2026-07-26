package com.knowflow.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 阅读进度上报请求参数，封装文档ID、进度百分比与阅读时长。
 */
@Data
public class ReadProgressDTO {

    @NotNull(message = "文档ID不能为空")
    private Long docId;

    @DecimalMin(value = "0", message = "进度不能小于0")
    @DecimalMax(value = "100", message = "进度不能大于100")
    /** 阅读进度，取值范围 0-100（百分比） */
    private BigDecimal progress;

    @Min(value = 0, message = "阅读秒数不能为负")
    private Integer readSeconds;
}
