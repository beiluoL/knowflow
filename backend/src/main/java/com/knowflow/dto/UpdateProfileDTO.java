package com.knowflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户资料更新请求参数，封装昵称、邮箱与头像地址。
 */
@Data
public class UpdateProfileDTO {

    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 255, message = "头像地址长度不能超过255个字符")
    private String avatar;
}
