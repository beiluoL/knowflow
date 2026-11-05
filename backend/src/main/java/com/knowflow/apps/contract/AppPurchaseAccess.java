package com.knowflow.apps.contract;

/** 商店购买事务使用的应用操作契约。 */
public interface AppPurchaseAccess {

    /** 返回官方应用是否可购买。 */
    boolean isActive(String appKey);

    /** 在当前购买事务中为买方安装并启用应用。 */
    void markInstalledAndEnabled(Long userId, String appKey);
}
