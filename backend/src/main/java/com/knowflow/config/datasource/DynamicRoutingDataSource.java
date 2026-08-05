package com.knowflow.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * 可热切换的动态数据源。
 * <p>
 * Spring 容器中注册的是本对象（引用恒定不变），内部持有真实 HikariDataSource 的原子引用。
 * 切换数据库时只替换内部引用，因此所有已注入 DataSource 的 Bean（MyBatis-Plus、
 * TransactionManager 等）无需重建即可立刻使用新库，实现运行时无重启切换。
 * <p>
 * <b>并发与安全说明：</b>
 * <ul>
 *   <li>getConnection() 每次都从当前引用取连接，切换瞬间之后的新请求走新库；</li>
 *   <li>旧连接池延迟关闭（由调用方控制），给正在执行的事务留出收尾时间，避免连接被硬中断；</li>
 *   <li>切换是「整库级」操作，切换期间的进行中事务属于旧库，不会被回滚到新库。</li>
 * </ul>
 */
@Slf4j
public class DynamicRoutingDataSource implements DataSource {

    /** 当前生效的真实数据源，切换时原子替换。 */
    private final AtomicReference<HikariDataSource> delegate = new AtomicReference<>();

    /** 当前生效的数据库类型，用于状态展示与方言判断。 */
    private final AtomicReference<DbTypeEnum> currentType = new AtomicReference<>();

    public DynamicRoutingDataSource(HikariDataSource initial, DbTypeEnum type) {
        this.delegate.set(initial);
        this.currentType.set(type);
    }

    /**
     * 原子替换底层数据源，返回被替换下来的旧数据源交由调用方关闭。
     *
     * @param newDataSource 已完成连通性校验的新数据源
     * @param newType       新数据源对应的数据库类型
     * @return 旧数据源（调用方负责延迟关闭）
     */
    public HikariDataSource swap(HikariDataSource newDataSource, DbTypeEnum newType) {
        HikariDataSource old = delegate.getAndSet(newDataSource);
        currentType.set(newType);
        log.info("数据源已切换: {} -> {}", old == null ? "无" : old.getJdbcUrl(), newDataSource.getJdbcUrl());
        return old;
    }

    /** 获取当前生效的数据库类型。 */
    public DbTypeEnum getCurrentType() {
        return currentType.get();
    }

    /** 获取当前底层数据源（用于读取连接池指标）。 */
    public HikariDataSource getDelegate() {
        return delegate.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.get().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate.get().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.get().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.get().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.get().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.get().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.get().getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.get().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.get().isWrapperFor(iface);
    }
}
