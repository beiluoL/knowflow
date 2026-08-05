package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据库切换 / 连接测试请求参数。
 * <p>
 * 仅 type 必填；其余连接参数留空时沿用服务端已有配置，
 * 便于前端「只切类型不改连接串」的常见场景。
 */
@Data
public class DbSwitchDTO {

    /** 目标数据库类型：h2 / mysql。 */
    @NotBlank(message = "数据库类型不能为空")
    private String type;

    /** JDBC 连接串，留空则沿用服务端配置。 */
    private String url;

    /** 用户名，留空则沿用服务端配置。 */
    private String username;

    /** 密码，留空则沿用服务端配置。 */
    private String password;

    /** 初始化模式：always / auto / never，留空则沿用服务端配置。 */
    private String initMode;

    /** 连接池最大连接数，非正数表示沿用服务端配置。 */
    private Integer maximumPoolSize;

    /** 是否在切换后执行初始化脚本（建表 + 演示数据）。 */
    private Boolean initSchema;
}
