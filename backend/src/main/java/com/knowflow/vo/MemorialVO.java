package com.knowflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 纪念日视图对象：供纪念日设置页列表展示。
 */
@Data
public class MemorialVO {

    private Long id;

    /** 纪念日名称 */
    private String name;

    /** fixed 固定日期 / yearly 每年重复 */
    private String type;

    /** 月-日 MM-dd */
    private String monthDay;

    /** 固定日期（type=fixed 时有值） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fixedDate;

    /** 标记颜色 */
    private String color;

    /** 备注 */
    private String note;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
