package com.knowflow.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReadProgressDTO {

    @NotNull(message = "文档ID不能为空")
    private Long docId;

    @DecimalMin(value = "0", message = "进度不能小于0")
    @DecimalMax(value = "100", message = "进度不能大于100")
    private BigDecimal progress;

    @Min(value = 0, message = "阅读秒数不能为负")
    private Integer readSeconds;
}
