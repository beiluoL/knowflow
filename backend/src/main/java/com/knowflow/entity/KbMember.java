package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_member")
/**
 * 知识库成员关联实体，承载「知识库-用户」的多对多关系与成员角色。
 * <p>
 * 角色枚举（持久化到 role 字段）：
 * <ul>
 *     <li>OWNER — 所有者：知识库创建者，拥有最高权限（成员管理、编辑、删除、邀请）</li>
 *     <li>EDITOR — 编辑者：可编辑/增删文档，不可管理成员与删除知识库</li>
 *     <li>READER — 阅读者：仅可查看文档与评论</li>
 * </ul>
 * 权限校验在 Service 层完成，数据库不建立物理外键，所有关联为逻辑外键。
 */
public class KbMember extends BaseEntity {

    /** 知识库 ID（逻辑外键 → doc_category.id）。 */
    private Long categoryId;

    /** 用户 ID（逻辑外键 → sys_user.id）。 */
    private Long userId;

    /** 成员角色：OWNER / EDITOR / READER。 */
    private String role;

    /** 邀请码（可选，当对未注册邮箱发送邀请时生成，注册后凭码激活加入）。 */
    private String inviteCode;

    /** 邀请目标邮箱（未注册用户的邀请记录用）。 */
    private String inviteEmail;

    /** 状态：0 已移除 / 1 生效。 */
    private Integer status;

    /** 加入时间（用户加入知识库的时间，接受邀请后填值）。 */
    private LocalDateTime joinTime;

    /** 成员角色枚举常量，避免魔法值。 */
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_EDITOR = "EDITOR";
    public static final String ROLE_READER = "READER";

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_REMOVED = 0;
}
