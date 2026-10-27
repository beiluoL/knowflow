package com.knowflow.dto;

import lombok.Data;

/**
 * 更新用户 App 状态请求（§32/§123）。
 * 不包含 userId（SecurityContext 获取，§33/§259）；appKey 走路径参数。
 */
@Data
public class UserAppUpdateRequest {

    /** 是否安装（缺省 true） */
    private Boolean installed;

    /** 是否启用（缺省 true；installed=false 时强制 false） */
    private Boolean enabled;

    /** 排序（>=0） */
    private Integer sortOrder;
}
