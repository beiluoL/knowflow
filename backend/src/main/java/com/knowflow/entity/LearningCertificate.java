package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数字证书实体（G-CERT-01）：学习路径完成后自动颁发，记录持证用户、路径与唯一验证码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_certificate")
public class LearningCertificate extends BaseEntity {

    /** 持证用户 ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 完成的学习路径 ID（逻辑外键 learning_path.id） */
    private Long pathId;

    /** 唯一证书验证码（可公开验证，格式如 KC-2026XXXX-XXXX） */
    private String certNo;

    /** 路径标题快照（颁发时固化，避免路径改名影响证书） */
    private String pathTitle;

    /** 持证用户名快照 */
    private String userName;

    /** 颁发时间 */
    private LocalDateTime issueDate;
}
