package com.knowflow.config.datasource;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Getter;

import java.util.Arrays;

/**
 * 支持的数据库类型枚举。
 * <p>
 * 集中维护「类型编码 - 驱动类 - MyBatis-Plus 分页方言 - 脚本目录」的映射关系，
 * 新增数据库类型只需在此扩展，避免驱动名等魔法值散落在各处。
 */
@Getter
public enum DbTypeEnum {

    /** H2 内存/文件数据库：用于开发与测试环境。 */
    H2("h2", "org.h2.Driver", DbType.H2, "db/h2", "H2 数据库（开发测试）"),

    /** MySQL：用于生产环境。 */
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", DbType.MYSQL, "db/mysql", "MySQL 数据库（生产）");

    /** 配置文件中使用的类型编码。 */
    private final String code;

    /** JDBC 驱动类全限定名。 */
    private final String driverClassName;

    /** MyBatis-Plus 分页插件所需的方言类型。 */
    private final DbType dbType;

    /** 方言脚本所在的 classpath 目录。 */
    private final String scriptDir;

    /** 展示名称，用于前端下拉与状态提示。 */
    private final String displayName;

    DbTypeEnum(String code, String driverClassName, DbType dbType, String scriptDir, String displayName) {
        this.code = code;
        this.driverClassName = driverClassName;
        this.dbType = dbType;
        this.scriptDir = scriptDir;
        this.displayName = displayName;
    }

    /**
     * 按编码解析类型，忽略大小写。
     *
     * @param code 类型编码（h2 / mysql）
     * @return 对应枚举
     * @throws IllegalArgumentException 编码不受支持时抛出，便于启动期 fail-fast
     */
    public static DbTypeEnum of(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "不支持的数据库类型: " + code + "，可选值: h2 / mysql"));
    }
}
