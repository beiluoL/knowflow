package com.knowflow.config.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据库切换配置：由 application.yml 的 knowflow.datasource.* 绑定。
 * <p>
 * type 决定启动时加载哪套数据源与方言脚本（h2 / mysql），
 * 实现「改配置即换库」，无需改动任何 Java 代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowflow.datasource")
public class DataSourceProperties {

    /** 当前启用的数据库类型：h2（开发测试）/ mysql（生产）。 */
    private String type = DbTypeEnum.H2.getCode();

    /** 是否允许运行时通过管理接口热切换数据源。生产可关闭以防误操作。 */
    private boolean allowRuntimeSwitch = true;

    /**
     * 外置配置文件路径：热切换后的数据库配置持久化于此。
     * <p>
     * 不写回 application.yml，避免污染打包产物；重启时优先读取该文件，
     * 从而保证「前端切换 → 重启后依旧生效」。
     */
    private String configFile = "./config/datasource.json";

    /** H2 连接配置（开发测试环境）。 */
    private Item h2 = new Item();

    /** MySQL 连接配置（生产环境）。 */
    private Item mysql = new Item();

    /** 单个数据源的连接参数与初始化策略。 */
    @Data
    public static class Item {
        private String driverClassName;
        private String url;
        private String username;
        private String password;

        /** 建表脚本位置（classpath:...），按方言区分。 */
        private String schemaLocation;

        /** 演示数据脚本位置（classpath:...），按方言区分。 */
        private String dataLocation;

        /**
         * 初始化模式：always（每次启动重建）/ never（不执行）/ auto（库为空时执行一次）。
         * H2 内存库用 always；MySQL 持久库用 auto，避免每次重启清空生产数据。
         */
        private String initMode = "auto";

        /** 连接池最大连接数。 */
        private int maximumPoolSize = 10;

        /** 连接池最小空闲连接数。 */
        private int minimumIdle = 2;

        /** 获取连接超时时间（毫秒）。 */
        private long connectionTimeout = 30000L;
    }
}
