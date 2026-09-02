package com.knowflow.vo;

import lombok.Data;

/** 用户应用状态展示（§122）。 */
@Data
public class UserAppVO {

    private String appKey;
    private Boolean installed;
    private Boolean enabled;
    private Integer sortOrder;
}
