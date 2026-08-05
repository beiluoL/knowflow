package com.knowflow.service;

import com.knowflow.dto.DbSwitchDTO;
import com.knowflow.vo.DbStatusVO;

import java.util.Map;

/**
 * 数据库管理服务：供后台「数据库设置」页查询状态、测试连接与热切换数据源。
 */
public interface DatabaseAdminService {

    /** 查询当前数据库运行状态与可选类型列表。 */
    DbStatusVO status();

    /** 测试目标数据库连通性，不影响当前运行数据源。 */
    Map<String, Object> testConnection(DbSwitchDTO dto);

    /**
     * 热切换数据源到目标数据库。
     *
     * @return 切换后的最新状态
     */
    DbStatusVO switchDataSource(DbSwitchDTO dto);

    /** 对当前数据库执行初始化脚本（建表 + 演示数据）。 */
    Map<String, Object> initSchema();
}
