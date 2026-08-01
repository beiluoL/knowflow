package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字证书视图对象（G-CERT-01）：展示证书详情及用于验证的信息。
 */
@Data
public class LearningCertificateVO {

    /** 证书 ID */
    private Long id;

    /** 持证用户 ID */
    private Long userId;

    /** 完成的学习路径 ID */
    private Long pathId;

    /** 唯一证书验证码 */
    private String certNo;

    /** 路径标题快照 */
    private String pathTitle;

    /** 持证用户名快照 */
    private String userName;

    /** 颁发时间 */
    private LocalDateTime issueDate;

    /** 是否当前用户本人持有（用于前端展示操作权限） */
    private Boolean mine;
}
