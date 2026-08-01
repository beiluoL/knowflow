package com.knowflow.vo;

import lombok.Data;

/** DAG 边：source 为前置章节，target 为依赖它的章节。 */
@Data
public class ChapterEdgeVO {

    /** 前置章节 ID。 */
    private Long source;

    /** 依赖该前置的章节 ID。 */
    private Long target;
}
