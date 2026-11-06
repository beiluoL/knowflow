package com.knowflow.store.constant;

/**
 * 经济层业务错误码（§76/§134-136）。
 * 前端 economy/constants.ts 按 code 映射友好文案（§286）。
 */
public final class EconomyError {

    public static final int INSUFFICIENT_BALANCE = 2001;
    public static final int ALREADY_OWNED = 2002;
    public static final int PRODUCT_DISABLED = 2003;
    public static final int APP_DISABLED = 2004;
    public static final int INVALID_PRODUCT = 2005;

    private EconomyError() {
    }
}
