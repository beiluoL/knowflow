package com.knowflow.apps.contract;

/** Apps operations used by the Store purchase transaction. */
public interface AppPurchaseAccess {

    /** Returns whether the official app is available for purchase. */
    boolean isActive(String appKey);

    /** Installs and enables the app for the buyer within the current purchase transaction. */
    void markInstalledAndEnabled(Long userId, String appKey);
}
