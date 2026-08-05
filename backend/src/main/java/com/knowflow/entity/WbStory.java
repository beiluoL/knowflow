package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库工作台·费曼故事（知识输出模块）。
 * 费曼技巧 + 故事化叙事：以教代学；gap_note 记录讲不通的卡点（知识漏洞）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_story")
public class WbStory extends BaseEntity {

    /** 所属用户ID */
    private Long userId;

    /** 来源收集箱条目ID（逻辑外键 wb_capture.id） */
    private Long captureId;

    /** 来源康奈尔笔记ID（逻辑外键 wb_note.id） */
    private Long noteId;

    /** 归属知识库/分类ID（逻辑外键 doc_category.id） */
    private Long categoryId;

    /** 故事标题 */
    private String title;

    /** 假想听众：CHILD 小孩 / NEWBIE 初学者 / PEER 同行 / INTERVIEWER 面试官 */
    private String audience;

    /** 核心类比/隐喻，如「把索引比作书的目录」 */
    private String metaphor;

    /** 故事正文（Markdown 叙事体） */
    private String content;

    /** 讲述卡点记录：没讲清楚的地方 = 知识漏洞 */
    private String gapNote;

    /** 状态：DRAFT 草稿 / DONE 已完成 / PUBLISHED 已分享 */
    private String status;

    /** 自评讲清程度：0~100 */
    private Integer clarityScore;

    /** 正文字数 */
    private Integer wordCount;
}
