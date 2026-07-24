package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_read_progress")
public class DocReadProgress extends BaseEntity {

    private Long userId;

    private Long docId;

    private BigDecimal progress;

    private Integer readSeconds;

    private LocalDateTime lastReadTime;
}
