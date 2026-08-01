package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 宠物更新 DTO，仅允许修改名称与头像标识。
 */
@Data
public class UserPetUpdateDTO {
    @NotBlank(message = "宠物名称不能为空")
    @Size(max = 50, message = "宠物名称不能超过50个字符")
    private String name;

    /** 宠物头像标识 */
    private String avatar;
}
