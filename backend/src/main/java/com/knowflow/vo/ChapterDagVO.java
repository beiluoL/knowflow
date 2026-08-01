package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/** 学习路径章节依赖关系图（DAG）。节点为章节，边为「前置章节 → 当前章节」的依赖。 */
@Data
public class ChapterDagVO {

    /** 节点：每个章节一个，含完成/解锁状态。 */
    private List<ChapterNodeVO> nodes;

    /** 边：由 prerequisiteChapterIds 解析出的依赖关系（source 为前置，target 为依赖方）。 */
    private List<ChapterEdgeVO> edges;
}
