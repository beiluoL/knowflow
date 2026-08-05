package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 数据库运行状态视图，用于后台「数据库设置」页展示。
 */
@Data
public class DbStatusVO {

    /** 当前生效的数据库类型编码（h2 / mysql）。 */
    private String currentType;

    /** 当前数据库展示名称。 */
    private String displayName;

    /** 当前 JDBC 连接串（密码不回传）。 */
    private String url;

    /** 连接用户名。 */
    private String username;

    /** 数据库产品名，如 MySQL / H2。 */
    private String productName;

    /** 数据库版本号。 */
    private String productVersion;

    /** 当前库中的业务表数量。 */
    private Integer tableCount;

    /** 连接是否健康。 */
    private Boolean healthy;

    /** 连接异常信息，健康时为空。 */
    private String message;

    /** 是否允许运行时热切换。 */
    private Boolean allowRuntimeSwitch;

    /** 连接池活跃连接数。 */
    private Integer activeConnections;

    /** 连接池空闲连接数。 */
    private Integer idleConnections;

    /** 连接池总连接数。 */
    private Integer totalConnections;

    /** 支持的数据库类型列表，供前端下拉选择。 */
    private List<DbOptionVO> options;

    /** 可选数据库类型描述。 */
    @Data
    public static class DbOptionVO {
        /** 类型编码。 */
        private String code;

        /** 展示名称。 */
        private String displayName;

        /** 该类型当前配置的连接串。 */
        private String url;

        /** 该类型配置的用户名。 */
        private String username;

        /** 初始化模式。 */
        private String initMode;

        /** 是否为当前生效类型。 */
        private Boolean active;
    }
}
