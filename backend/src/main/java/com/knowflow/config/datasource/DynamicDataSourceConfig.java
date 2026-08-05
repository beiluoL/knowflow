package com.knowflow.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 动态数据源装配。
 * <p>
 * 启动时按 knowflow.datasource.type（外置 JSON 优先）选择 H2 或 MySQL，
 * 构建连接池、执行对应方言的初始化脚本，并以 {@link DynamicRoutingDataSource}
 * 作为 @Primary DataSource 注入容器，从而支持后续运行时热切换。
 * <p>
 * 由于本类显式提供了 DataSource Bean，Spring Boot 的 DataSourceAutoConfiguration
 * 不再生效，spring.datasource.* 与 spring.sql.init.* 均由本配置接管。
 */
@Slf4j
@Configuration
public class DynamicDataSourceConfig {

    /** 数据源管理器：构建、测试、切换与持久化的统一入口。 */
    @Bean
    public DataSourceManager dataSourceManager(DataSourceProperties properties) {
        return new DataSourceManager(properties);
    }

    /**
     * 主数据源。返回动态包装对象，容器内引用恒定，便于热切换。
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "dynamicRoutingDataSource")
    public DynamicRoutingDataSource dynamicRoutingDataSource(DataSourceProperties properties,
                                                             DataSourceManager manager) {
        // 外置配置（前端切换结果）优先于 application.yml，保证重启后仍生效
        String persisted = manager.readPersistedType();
        String effective = persisted != null ? persisted : properties.getType();
        DbTypeEnum type = DbTypeEnum.of(effective);
        log.info("启动数据源类型: {} ({}), 来源: {}", type.getCode(), type.getDisplayName(),
                persisted != null ? "外置配置文件" : "application.yml");

        DataSourceProperties.Item item = manager.resolveItem(type);
        HikariDataSource real = manager.buildDataSource(type, item);
        // 执行方言脚本：H2 每次重建，MySQL 默认仅首次建表
        manager.initSchemaIfNeeded(real::getConnection, type, item);
        return new DynamicRoutingDataSource(real, type);
    }

    /**
     * 暴露为标准 DataSource 类型，供 MyBatis-Plus / 事务管理器等按接口注入。
     */
    @Bean
    public DataSource dataSource(DynamicRoutingDataSource dynamicRoutingDataSource) {
        return dynamicRoutingDataSource;
    }
}
