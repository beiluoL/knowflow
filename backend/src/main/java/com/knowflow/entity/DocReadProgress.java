package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_read_progress")
/** 文档阅读进度实体，记录用户对某篇文档的阅读位置与累计阅读时长。 */
public class DocReadProgress extends BaseEntity {

    private Long userId;

    private Long docId;

    /** 阅读进度比例，取值范围 0~1。 */
    private BigDecimal progress;

    /** 累计阅读时长（秒）。 */
    private Integer readSeconds;

    private LocalDateTime lastReadTime;
}
