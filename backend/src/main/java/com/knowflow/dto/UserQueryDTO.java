package com.knowflow.dto;

import com.knowflow.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery {

    private String keyword;

    private String role;

    private Integer status;
}
