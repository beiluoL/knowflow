package com.knowflow.config.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据源构建与切换管理器。
 * <p>
 * 职责：
 * <ol>
 *   <li>按 {@link DbTypeEnum} 构建 Hikari 连接池；</li>
 *   <li>执行对应方言目录下的 schema.sql / data.sql 初始化脚本；</li>
 *   <li>测试目标库连通性（不影响当前运行库）；</li>
 *   <li>热切换动态数据源并延迟释放旧连接池；</li>
 *   <li>把切换结果持久化到外置 JSON，保证重启后仍生效。</li>
 * </ol>
 */
@Slf4j
public class DataSourceManager {

    /** 旧连接池延迟关闭时间（秒）：给切换瞬间仍在执行的事务留出收尾窗口。 */
    private static final long OLD_POOL_CLOSE_DELAY_SECONDS = 30L;

    /** 连通性测试的超时时间（秒）。 */
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    private final DataSourceProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 单线程延迟关闭旧连接池。使用 ThreadPoolExecutor 系工厂方法并指定具名线程，
     * 便于问题定位（遵循阿里规约：线程需有明确命名）。
     */
    private final ScheduledExecutorService poolCloser = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ds-pool-closer-" + seq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

    public DataSourceManager(DataSourceProperties properties) {
        this.properties = properties;
    }

    /**
     * 读取目标类型对应的连接参数。
     * <p>
     * 外置配置文件优先级高于 application.yml，以承载前端热切换后的最新配置。
     */
    public DataSourceProperties.Item resolveItem(DbTypeEnum type) {
        DataSourceProperties.Item base = DbTypeEnum.MYSQL == type ? properties.getMysql() : properties.getH2();
        DataSourceProperties.Item override = readOverride(type);
        if (override == null) {
            return base;
        }
        // 外置文件仅覆盖非空字段，缺省项继续沿用 yml，避免部分配置丢失
        DataSourceProperties.Item merged = new DataSourceProperties.Item();
        merged.setDriverClassName(pick(override.getDriverClassName(), base.getDriverClassName()));
        merged.setUrl(pick(override.getUrl(), base.getUrl()));
        merged.setUsername(pick(override.getUsername(), base.getUsername()));
        merged.setPassword(override.getPassword() != null ? override.getPassword() : base.getPassword());
        merged.setSchemaLocation(pick(override.getSchemaLocation(), base.getSchemaLocation()));
        merged.setDataLocation(pick(override.getDataLocation(), base.getDataLocation()));
        merged.setInitMode(pick(override.getInitMode(), base.getInitMode()));
        merged.setMaximumPoolSize(override.getMaximumPoolSize() > 0
                ? override.getMaximumPoolSize() : base.getMaximumPoolSize());
        merged.setMinimumIdle(override.getMinimumIdle() > 0
                ? override.getMinimumIdle() : base.getMinimumIdle());
        merged.setConnectionTimeout(override.getConnectionTimeout() > 0
                ? override.getConnectionTimeout() : base.getConnectionTimeout());
        return merged;
    }

    private String pick(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    /**
     * 构建 Hikari 连接池。驱动类以枚举为准，杜绝配置写错驱动导致的类型/方言错配。
     */
    public HikariDataSource buildDataSource(DbTypeEnum type, DataSourceProperties.Item item) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(type.getDriverClassName());
        config.setJdbcUrl(item.getUrl());
        config.setUsername(item.getUsername());
        config.setPassword(item.getPassword());
        config.setMaximumPoolSize(item.getMaximumPoolSize());
        config.setMinimumIdle(item.getMinimumIdle());
        config.setConnectionTimeout(item.getConnectionTimeout());
        config.setPoolName("knowflow-" + type.getCode());
        // 快速失败：初始化时若连不上直接抛错，避免静默降级
        config.setInitializationFailTimeout(item.getConnectionTimeout());
        return new HikariDataSource(config);
    }

    /**
     * 测试目标数据库连通性，不改变当前运行数据源。
     *
     * @return 测试结果，含是否成功、耗时、数据库版本或错误信息
     */
    public Map<String, Object> testConnection(DbTypeEnum type, DataSourceProperties.Item item) {
        Map<String, Object> result = new HashMap<>(8);
        result.put("type", type.getCode());
        result.put("url", item.getUrl());
        long start = System.currentTimeMillis();
        HikariDataSource probe = null;
        try {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName(type.getDriverClassName());
            config.setJdbcUrl(item.getUrl());
            config.setUsername(item.getUsername());
            config.setPassword(item.getPassword());
            // 探针池只需 1 条连接，超时收紧，避免长时间阻塞管理接口
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(VALIDATION_TIMEOUT_SECONDS));
            config.setInitializationFailTimeout(TimeUnit.SECONDS.toMillis(VALIDATION_TIMEOUT_SECONDS));
            config.setPoolName("knowflow-probe-" + type.getCode());
            probe = new HikariDataSource(config);
            try (Connection conn = probe.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                result.put("success", true);
                result.put("productName", meta.getDatabaseProductName());
                result.put("productVersion", meta.getDatabaseProductVersion());
                result.put("driverVersion", meta.getDriverVersion());
                result.put("tableCount", countTables(conn));
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", rootMessage(e));
            log.warn("数据库连通性测试失败: type={}, url={}, err={}", type.getCode(), item.getUrl(), rootMessage(e));
        } finally {
            if (probe != null) {
                probe.close();
            }
            result.put("costMs", System.currentTimeMillis() - start);
        }
        return result;
    }

    /**
     * 按初始化模式执行方言脚本。
     * <p>
     * always：每次执行；auto：仅当库中无业务表时执行；never：跳过。
     * 生产 MySQL 建议使用 auto，避免重启清空数据。
     */
    public void initSchemaIfNeeded(DataSource0 wrapper, DbTypeEnum type, DataSourceProperties.Item item) {
        String mode = item.getInitMode() == null ? "auto" : item.getInitMode().trim().toLowerCase();
        if ("never".equals(mode)) {
            log.info("[{}] 初始化模式=never，跳过脚本执行", type.getCode());
            return;
        }
        try (Connection conn = wrapper.getConnection()) {
            if ("auto".equals(mode) && countTables(conn) > 0) {
                log.info("[{}] 初始化模式=auto 且已存在业务表，跳过脚本执行", type.getCode());
                return;
            }
            runScript(conn, item.getSchemaLocation(), type, "schema");
            runScript(conn, item.getDataLocation(), type, "data");
        } catch (SQLException e) {
            throw new IllegalStateException("执行数据库初始化脚本失败: " + rootMessage(e), e);
        }
    }

    /** 执行单个脚本文件，脚本不存在时跳过（允许生产环境不带演示数据）。 */
    private void runScript(Connection conn, String location, DbTypeEnum type, String label) {
        if (location == null || location.isBlank()) {
            return;
        }
        String path = location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("[{}] {} 脚本不存在，跳过: {}", type.getCode(), label, path);
            return;
        }
        long start = System.currentTimeMillis();
        // continueOnError=false 保证脚本错误立即暴露；ignoreFailedDrops=true 容忍 DROP 不存在对象
        ScriptUtils.executeSqlScript(conn, new org.springframework.core.io.support.EncodedResource(
                        resource, StandardCharsets.UTF_8),
                false, true,
                ScriptUtils.DEFAULT_COMMENT_PREFIX,
                ScriptUtils.DEFAULT_STATEMENT_SEPARATOR,
                ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER);
        log.info("[{}] {} 脚本执行完成: {} ({}ms)", type.getCode(), label, path, System.currentTimeMillis() - start);
    }

    /** 统计当前库中的业务表数量，用于 auto 初始化判断与状态展示。 */
    public int countTables(Connection conn) {
        try {
            String catalog = conn.getCatalog();
            try (ResultSet rs = conn.getMetaData().getTables(catalog, null, "%", new String[]{"TABLE"})) {
                int count = 0;
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    // 过滤 H2/MySQL 的系统 schema，只统计业务表
                    if (schema == null || !isSystemSchema(schema)) {
                        count++;
                    }
                }
                return count;
            }
        } catch (SQLException e) {
            log.warn("统计表数量失败: {}", rootMessage(e));
            return -1;
        }
    }

    private boolean isSystemSchema(String schema) {
        String s = schema.toUpperCase();
        return "INFORMATION_SCHEMA".equals(s) || "PERFORMANCE_SCHEMA".equals(s)
                || "MYSQL".equals(s) || "SYS".equals(s);
    }

    /** 延迟关闭旧连接池，避免切换瞬间中断进行中的事务。 */
    public void closeLater(HikariDataSource old) {
        if (old == null || old.isClosed()) {
            return;
        }
        poolCloser.schedule(() -> {
            try {
                old.close();
                log.info("旧连接池已释放: {}", old.getPoolName());
            } catch (Exception e) {
                log.warn("释放旧连接池失败: {}", rootMessage(e));
            }
        }, OLD_POOL_CLOSE_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 持久化当前生效配置到外置 JSON 文件，保证重启后沿用前端所选数据库。
     */
    public void persist(DbTypeEnum type, DataSourceProperties.Item item) {
        try {
            File file = new File(properties.getConfigFile());
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                log.warn("创建配置目录失败: {}", dir.getAbsolutePath());
            }
            Map<String, Object> payload = new HashMap<>(4);
            payload.put("type", type.getCode());
            payload.put(type.getCode(), item);
            // 保留另一套配置，避免来回切换时丢失
            DbTypeEnum other = type == DbTypeEnum.MYSQL ? DbTypeEnum.H2 : DbTypeEnum.MYSQL;
            payload.put(other.getCode(), resolveItem(other));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, payload);
            log.info("数据源配置已持久化: {}", file.getAbsolutePath());
        } catch (Exception e) {
            log.error("持久化数据源配置失败: {}", rootMessage(e), e);
        }
    }

    /** 读取外置配置中当前应生效的数据库类型；文件不存在时返回 null 表示沿用 yml。 */
    public String readPersistedType() {
        Map<String, Object> root = readRoot();
        if (root == null) {
            return null;
        }
        Object type = root.get("type");
        return type == null ? null : String.valueOf(type);
    }

    /** 读取外置配置中指定类型的连接参数覆盖项。 */
    private DataSourceProperties.Item readOverride(DbTypeEnum type) {
        Map<String, Object> root = readRoot();
        if (root == null) {
            return null;
        }
        Object node = root.get(type.getCode());
        if (node == null) {
            return null;
        }
        return objectMapper.convertValue(node, DataSourceProperties.Item.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRoot() {
        File file = new File(properties.getConfigFile());
        if (!file.exists() || file.length() == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(file, Map.class);
        } catch (Exception e) {
            log.warn("读取外置数据源配置失败，将回退至 application.yml: {}", rootMessage(e));
            return null;
        }
    }

    /** 提取最内层异常信息，便于前端展示可读的失败原因。 */
    public static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    /** 便于测试与解耦的最小连接提供接口。 */
    @FunctionalInterface
    public interface DataSource0 {
        Connection getConnection() throws SQLException;
    }
}
