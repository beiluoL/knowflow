package com.knowflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 日历日期标记（Date Mark）视图对象：法定节假日 / 传统节日 / 现代节日 / 纪念日。
 * 月、周、日三视图统一消费，按 date 区间一次拉取，保证视图切换数据一致。
 */
@Data
public class DateMarkVO {

    /**
     * 标记类型：
     * holiday  法定节假日与调休（subLabel 为「休」/「班」）
     * lunar    中国传统节日（农历计算）
     * modern   现代节日（公历/按周计算）
     * memorial 用户自定义纪念日
     */
    private String type;

    /** 标记日期 yyyy-MM-dd */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 标记名称（如「国庆节」「中秋节」「母亲节」） */
    private String name;

    /** 辅助标签：holiday 为「休」/「班」；lunar 为农历月日（如「八月十五」） */
    private String subLabel;

    /** 主题色（十六进制，前端渲染用） */
    private String color;

    /** 纪念日 ID（type=memorial 时有值，供编辑跳转） */
    private Long memorialId;
}
