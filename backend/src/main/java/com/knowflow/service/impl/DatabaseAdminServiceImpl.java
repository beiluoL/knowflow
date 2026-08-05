package com.knowflow.service.impl;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.knowflow.config.datasource.DataSourceManager;
import com.knowflow.config.datasource.DataSourceProperties;
import com.knowflow.config.datasource.DbTypeEnum;
import com.knowflow.config.datasource.DynamicRoutingDataSource;
import com.knowflow.dto.DbSwitchDTO;
import com.knowflow.service.DatabaseAdminService;
import com.knowflow.vo.DbStatusVO;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据库管理服务实现。
 * <p>
 * 切换流程遵循「先验证、后替换」：先用探针连接目标库确认可用，
 * 再构建正式连接池并原子替换，最后延迟释放旧池。任一步失败都不会影响当前运行库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseAdminServiceImpl implements DatabaseAdminService {

    private final DynamicRoutingDataSource dynamicRoutingDataSource;
    private final DataSourceManager dataSourceManager;
    private final DataSourceProperties dataSourceProperties;
    private final MybatisPlusInterceptor mybatisPlusInterceptor;

    /** 切换串行化：避免并发切换导致连接池泄漏或状态错乱。 */
    private final ReentrantLock switchLock = new ReentrantLock();

    @Override
    public DbStatusVO status() {
        DbTypeEnum current = dynamicRoutingDataSource.getCurrentType();
        DataSourceProperties.Item item = dataSourceManager.resolveItem(current);

        DbStatusVO vo = new DbStatusVO();
        vo.setCurrentType(current.getCode());
        vo.setDisplayName(current.getDisplayName());
        vo.setUrl(item.getUrl());
        vo.setUsername(item.getUsername());
        vo.setAllowRuntimeSwitch(dataSourceProperties.isAllowRuntimeSwitch());

        // 读取实时连接与连接池指标
        try (Connection conn = dynamicRoutingDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            vo.setProductName(meta.getDatabaseProductName());
            vo.setProductVersion(meta.getDatabaseProductVersion());
            vo.setTableCount(dataSourceManager.countTables(conn));
            vo.setHealthy(true);
        } catch (Exception e) {
            vo.setHealthy(false);
            vo.setMessage(DataSourceManager.rootMessage(e));
        }

        HikariDataSource delegate = dynamicRoutingDataSource.getDelegate();
        if (delegate != null && !delegate.isClosed()) {
            try {
                HikariPoolMXBean pool = delegate.getHikariPoolMXBean();
                vo.setActiveConnections(pool.getActiveConnections());
                vo.setIdleConnections(pool.getIdleConnections());
                vo.setTotalConnections(pool.getTotalConnections());
            } catch (Exception e) {
                log.debug("读取连接池指标失败: {}", DataSourceManager.rootMessage(e));
            }
        }

        vo.setOptions(buildOptions(current));
        return vo;
    }

    /** 组装可选数据库列表，密码不回传前端。 */
    private List<DbStatusVO.DbOptionVO> buildOptions(DbTypeEnum current) {
        List<DbStatusVO.DbOptionVO> options = new ArrayList<>(DbTypeEnum.values().length);
        for (DbTypeEnum type : DbTypeEnum.values()) {
            DataSourceProperties.Item item = dataSourceManager.resolveItem(type);
            DbStatusVO.DbOptionVO option = new DbStatusVO.DbOptionVO();
            option.setCode(type.getCode());
            option.setDisplayName(type.getDisplayName());
            option.setUrl(item.getUrl());
            option.setUsername(item.getUsername());
            option.setInitMode(item.getInitMode());
            option.setActive(type == current);
            options.add(option);
        }
        return options;
    }

    @Override
    public Map<String, Object> testConnection(DbSwitchDTO dto) {
        DbTypeEnum type = DbTypeEnum.of(dto.getType());
        return dataSourceManager.testConnection(type, mergeItem(type, dto));
    }

    @Override
    public DbStatusVO switchDataSource(DbSwitchDTO dto) {
        if (!dataSourceProperties.isAllowRuntimeSwitch()) {
            throw new IllegalStateException("运行时切换已被禁用（knowflow.datasource.allow-runtime-switch=false）");
        }
        DbTypeEnum target = DbTypeEnum.of(dto.getType());
        DataSourceProperties.Item item = mergeItem(target, dto);

        // 加锁保证同一时刻只有一次切换在执行
        if (!switchLock.tryLock()) {
            throw new IllegalStateException("另一次数据库切换正在进行中，请稍后重试");
        }
        try {
            // 步骤 1：探针校验目标库连通性，失败则中止，绝不影响当前运行库
            Map<String, Object> test = dataSourceManager.testConnection(target, item);
            if (!Boolean.TRUE.equals(test.get("success"))) {
                throw new IllegalStateException("目标数据库连接失败: " + test.get("message"));
            }

            // 步骤 2：构建新连接池
            HikariDataSource newPool = dataSourceManager.buildDataSource(target, item);
            try {
                // 步骤 3：按需初始化表结构与演示数据
                if (Boolean.TRUE.equals(dto.getInitSchema())) {
                    // 前端显式勾选「初始化」时强制执行脚本
                    DataSourceProperties.Item forced = copyOf(item);
                    forced.setInitMode("always");
                    dataSourceManager.initSchemaIfNeeded(newPool::getConnection, target, forced);
                } else {
                    dataSourceManager.initSchemaIfNeeded(newPool::getConnection, target, item);
                }
            } catch (RuntimeException e) {
                // 初始化失败需回收新池，避免连接泄漏
                newPool.close();
                throw e;
            }

            // 步骤 4：原子替换并同步分页方言
            HikariDataSource old = dynamicRoutingDataSource.swap(newPool, target);
            refreshPaginationDialect(target);

            // 步骤 5：持久化配置，保证重启后仍使用该库
            dataSourceManager.persist(target, item);

            // 步骤 6：延迟释放旧池，给进行中的事务收尾时间
            dataSourceManager.closeLater(old);

            log.info("数据库已热切换为: {} ({})", target.getCode(), target.getDisplayName());
            return status();
        } finally {
            switchLock.unlock();
        }
    }

    /**
     * 切库后同步刷新 MyBatis-Plus 分页方言。
     * <p>
     * 分页拦截器是单例且已被 SqlSessionFactory 持有，无法整体替换，
     * 因此就地修改其 DbType，使新库的 LIMIT 语法正确生成。
     */
    private void refreshPaginationDialect(DbTypeEnum target) {
        List<InnerInterceptor> inners = mybatisPlusInterceptor.getInterceptors();
        for (InnerInterceptor inner : inners) {
            if (inner instanceof PaginationInnerInterceptor pagination) {
                pagination.setDbType(target.getDbType());
                log.info("分页方言已切换为: {}", target.getDbType());
            }
        }
    }

    @Override
    public Map<String, Object> initSchema() {
        DbTypeEnum current = dynamicRoutingDataSource.getCurrentType();
        DataSourceProperties.Item item = copyOf(dataSourceManager.resolveItem(current));
        item.setInitMode("always");

        Map<String, Object> result = new HashMap<>(4);
        long start = System.currentTimeMillis();
        try {
            dataSourceManager.initSchemaIfNeeded(dynamicRoutingDataSource::getConnection, current, item);
            try (Connection conn = dynamicRoutingDataSource.getConnection()) {
                result.put("tableCount", dataSourceManager.countTables(conn));
            }
            result.put("success", true);
            result.put("message", "初始化脚本执行完成");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", DataSourceManager.rootMessage(e));
            log.error("执行初始化脚本失败", e);
        }
        result.put("type", current.getCode());
        result.put("costMs", System.currentTimeMillis() - start);
        return result;
    }

    /** 用请求参数覆盖服务端配置，未传字段沿用原值。 */
    private DataSourceProperties.Item mergeItem(DbTypeEnum type, DbSwitchDTO dto) {
        DataSourceProperties.Item base = copyOf(dataSourceManager.resolveItem(type));
        if (dto.getUrl() != null && !dto.getUrl().isBlank()) {
            base.setUrl(dto.getUrl().trim());
        }
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            base.setUsername(dto.getUsername().trim());
        }
        // 密码允许显式设置为空串（如 H2 无密码），故只判 null
        if (dto.getPassword() != null) {
            base.setPassword(dto.getPassword());
        }
        if (dto.getInitMode() != null && !dto.getInitMode().isBlank()) {
            base.setInitMode(dto.getInitMode().trim());
        }
        if (dto.getMaximumPoolSize() != null && dto.getMaximumPoolSize() > 0) {
            base.setMaximumPoolSize(dto.getMaximumPoolSize());
        }
        // 驱动始终以枚举为准，避免前端传错驱动名
        base.setDriverClassName(type.getDriverClassName());
        return base;
    }

    /** 浅拷贝配置项，避免修改影响缓存中的原始配置。 */
    private DataSourceProperties.Item copyOf(DataSourceProperties.Item src) {
        DataSourceProperties.Item copy = new DataSourceProperties.Item();
        copy.setDriverClassName(src.getDriverClassName());
        copy.setUrl(src.getUrl());
        copy.setUsername(src.getUsername());
        copy.setPassword(src.getPassword());
        copy.setSchemaLocation(src.getSchemaLocation());
        copy.setDataLocation(src.getDataLocation());
        copy.setInitMode(src.getInitMode());
        copy.setMaximumPoolSize(src.getMaximumPoolSize());
        copy.setMinimumIdle(src.getMinimumIdle());
        copy.setConnectionTimeout(src.getConnectionTimeout());
        return copy;
    }

    /** 供健康检查等场景复用的支持类型列表。 */
    public List<String> supportedTypes() {
        return Arrays.stream(DbTypeEnum.values()).map(DbTypeEnum::getCode).toList();
    }
}
