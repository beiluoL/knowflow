package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_favorite")
public class DocFavorite extends BaseEntity {

    private Long userId;

    private Long docId;
}
