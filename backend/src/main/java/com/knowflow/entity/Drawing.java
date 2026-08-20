package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 绘图编辑器·流程图（Drawing）：整图以 JSON 形式持久化在 data 列。
 * data 结构（vue-flow 契约）：{ nodes:[{id,position:{x,y},data:{label},type}], edges:[{id,source,target}] }。
 * type 列用于区分图类型（flowchart 流程图 / 其它预留），便于后续扩展多种图形。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_drawing")
public class Drawing extends BaseEntity {

    /** 所属用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 绘图标题 */
    private String title;

    /** 图类型（flowchart 等），默认 flowchart */
    private String type;

    /** 整图数据（JSON 文本）：节点 / 连线 */
    private String data;
}
