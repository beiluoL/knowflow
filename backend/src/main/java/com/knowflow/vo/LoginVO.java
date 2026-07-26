package com.knowflow.vo;

import lombok.Data;

/**
 * 登录响应视图对象，返回认证令牌与登录用户信息。
 */
@Data
public class LoginVO {

    private String token;

    private UserVO user;
}
