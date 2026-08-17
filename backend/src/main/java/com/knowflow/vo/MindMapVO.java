package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 思维导图详情 VO：data 为已解析的整图 JSON 对象（前端直接消费）。
 */
@Data
public class MindMapVO {

    private Long id;
    private Long userId;
    private String title;
    /** 解析后的整图数据对象（LinkedHashMap / ArrayList） */
    private Object data;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
