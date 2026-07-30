package com.knowflow.vo;

import lombok.Data;

/** 实体抽取结果汇总（单篇/批量）。 */
@Data
public class ExtractResultVO {
    /** 已处理文档数。 */
    private Integer docCount;
    /** 累计抽取实体数（按篇计数，含已合并去重）。 */
    private Integer entityCount;
    /** 累计抽取关系数（按篇计数，含去重跳过）。 */
    private Integer relationCount;
    /** 结果说明。 */
    private String message;
}
