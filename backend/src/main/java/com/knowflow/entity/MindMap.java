package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习工作台·思维导图（MindMap）：整图以 JSON 形式持久化在 data 列。
 * data 结构：{ nodes:[{id,text,x,y,parentId,collapsed,color}], edges:[{id,source,target}], view:{scale,tx,ty} }。
 * 层级关系通过节点的 parentId 表达，自由连线通过 edges 表达（见前端画布交互）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_mindmap")
public class MindMap extends BaseEntity {

    /** 所属用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 思维导图标题 */
    private String title;

    /** 整图数据（JSON 文本）：节点 / 连线 / 视图变换 */
    private String data;

    /** 归属知识库/分类ID（逻辑外键 doc_category.id，可空） */
    private Long categoryId;
}
