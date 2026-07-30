package com.knowflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 邀请成员加入小组请求参数
 */
@Data
public class GroupInviteDTO {

    @NotNull(message = "小组ID不能为空")
    private Long groupId;

    @Email(message = "邮箱格式不正确")
    private String email;

    private Long userId;

    /** 邀请角色：ADMIN-管理员，MEMBER-普通成员 */
    private String role = "MEMBER";
}