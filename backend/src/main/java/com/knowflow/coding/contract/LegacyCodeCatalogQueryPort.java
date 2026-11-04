package com.knowflow.coding.contract;

import com.fasterxml.jackson.databind.JsonNode;

/** 管理目录读取旧版代码题的公开契约。 */
public interface LegacyCodeCatalogQueryPort {

    /**
     * 返回现有管理端详情 JSON 投影，保留原字段和空值，供编辑令牌计算。
     *
     * @return 详情投影，题目不存在或已逻辑删除时返回 null
     */
    JsonNode findDetail(long questionId);
}
