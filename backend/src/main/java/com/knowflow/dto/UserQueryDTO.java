package com.knowflow.dto;

import com.knowflow.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户列表分页查询条件，支持关键字、角色与状态过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery {

    private String keyword;

    /** 角色编码，如 admin、user（具体以枚举为准） */
    private String role;

    /** 用户状态编码（具体以枚举为准） */
    private Integer status;
}
