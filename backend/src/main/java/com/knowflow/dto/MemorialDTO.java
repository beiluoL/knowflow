package com.knowflow.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 纪念日新增/更新入参。
 * type=fixed 时需传 fixedDate；type=yearly 时需传 monthDay（MM-dd）。
 * 服务层会统一维护 monthDay（fixed 类型由 fixedDate 派生冗余）。
 */
@Data
public class MemorialDTO {

    /** 纪念日名称（必填） */
    private String name;

    /** fixed 固定日期 / yearly 每年重复（必填） */
    private String type;

    /** 月-日 MM-dd（yearly 必填；fixed 可留空，由 fixedDate 派生） */
    private String monthDay;

    /** 固定日期 yyyy-MM-dd（type=fixed 必填） */
    private LocalDate fixedDate;

    /** 标记颜色（十六进制，可空则取默认） */
    private String color;

    /** 备注 */
    private String note;
}
