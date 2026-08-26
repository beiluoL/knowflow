package com.knowflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 计划批量生成入参 DTO。
 */
@Data
public class PlanGenerateDTO {

    /** 起始日期，默认下周日开始的周一。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 覆盖生成天数，默认 7（一整周）。 */
    private Integer days;

    /** 是否强制覆盖：true 时先物理删除同日旧计划再重生成。 */
    private Boolean force;
}
