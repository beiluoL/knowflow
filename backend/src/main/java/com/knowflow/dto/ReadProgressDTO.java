package com.knowflow.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReadProgressDTO {

    private Long docId;

    private BigDecimal progress;

    private Integer readSeconds;
}
