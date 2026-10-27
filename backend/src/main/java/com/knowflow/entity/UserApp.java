package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户应用状态（Architecture-3：Backend Source of Truth，§19/§419）。
 * UserApp = 用户启用/显示状态；Entitlement = 拥有资格（§8/§39，二者独立）。
 * 停用 = enabled=false，绝不删除 Entitlement 与业务数据（§75/§142）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_app")
public class UserApp extends BaseEntity {

    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** AppDefinition.key（uk_user_app: user_id+app_key 唯一） */
    private String appKey;

    /** 是否安装（1/0） */
    private Integer installed;

    /** 是否启用（1/0；installed=false 时强制为 0） */
    private Integer enabled;

    /** 排序（越小越靠前，默认按 Registry 顺序） */
    private Integer sortOrder;

    /** 最近打开（未来推荐用；本阶段不写入，§159/§209-211） */
    private LocalDateTime lastOpenAt;
}
