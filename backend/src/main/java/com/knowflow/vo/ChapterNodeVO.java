package com.knowflow.vo;

import lombok.Data;

/** DAG 节点：单个学习章节。status 用于前端着色（已完成/可学/锁定）。 */
@Data
public class ChapterNodeVO {

    /** 章节 ID。 */
    private Long id;

    /** 章节标题。 */
    private String title;

    /** 排序号。 */
    private Integer sortOrder;

    /** 预计时长（分钟）。 */
    private Integer duration;

    /** 状态：completed=已完成 / available=可学（已解锁未完） / locked=未解锁（前置未完成）。 */
    private String status;

    /** 前置章节 ID 列表（原样透传，逗号分隔）。 */
    private String prerequisiteChapterIds;
}
