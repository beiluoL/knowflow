package com.knowflow.reward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 宠物更新 DTO，仅允许修改名称与头像标识。 */
@Data
public class UserPetUpdateDTO {
  /** 前端传入的宠物名称，必填且不能全为空白，最多 50 个字符；更新时直接保存，不裁剪空格。 */
  @NotBlank(message = "宠物名称不能为空")
  @Size(max = 50, message = "宠物名称不能超过50个字符")
  private String name;

  /** 前端传入的可选头像标识；null、空串或全空白均保留原头像，非空白值直接保存，当前未校验标识集合或长度。 */
  private String avatar;
}
