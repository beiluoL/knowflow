package com.knowflow.interview.verify.dynamicproxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 统一织入的 InvocationHandler：计数 + 计时 + 转发到真实对象。
 * 用 AtomicReference 持有 proxy 自身，用于演示「在 invoke 内调用 proxy 方法会无限递归」。
 */
public class TraceHandler implements InvocationHandler {
    private final Object target;
    public int invokes = 0;
    public final AtomicReference<Object> proxyRef = new AtomicReference<>();

    public TraceHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        proxyRef.set(proxy);
        invokes++;
        return method.invoke(target, args); // 转发真实对象
    }
}
