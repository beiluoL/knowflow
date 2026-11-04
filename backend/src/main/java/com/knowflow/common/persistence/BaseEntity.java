package com.knowflow.common.persistence;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
/** 实体公共基类，统一持有主键、创建/更新时间及逻辑删除字段。 */
public class BaseEntity implements Serializable {

  @TableId(type = IdType.AUTO)
  private Long id;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

  @TableLogic
  @TableField(fill = FieldFill.INSERT)
  /** 逻辑删除标记，0 未删 / 1 已删，由 @TableLogic 自动处理。 */
  private Integer deleted;
}
