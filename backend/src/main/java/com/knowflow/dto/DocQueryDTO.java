package com.knowflow.dto;

import com.knowflow.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档列表分页查询条件，支持关键字、分类、难度与状态过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocQueryDTO extends PageQuery {

    private String keyword;

    private Long categoryId;

    /** 难度等级编码（具体以枚举为准） */
    private Integer difficulty;

    /** 文档状态编码（具体以枚举为准） */
    private Integer status;

    /**
     * 排序方式：relevance（相关度，仅在有 keyword 时生效）/ time（最新）/ view（最热）。
     * 缺省：有关键词按相关度，无关键词按时间。
     */
    private String sort;

    /**
     * 文档类型过滤（服务端下推，保证分页/总数正确）：doc / pdf / markdown / note。
     * 由文件名后缀与 tags 推导，与历史前端启发式口径保持一致。
     */
    private String type;

    /** 创建时间下界（含），ISO 日期 yyyy-MM-dd。 */
    private String from;

    /** 创建时间上界（含当天），ISO 日期 yyyy-MM-dd。 */
    private String to;
}
