package com.knowflow.knowledge.entity;

import com.knowflow.common.persistence.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_favorite")
/** 文档收藏关系实体，记录用户收藏的文档。 */
public class DocFavorite extends BaseEntity {

    private Long userId;

    private Long docId;
}
