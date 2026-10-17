package com.knowflow.interview.verify.dynamicproxy;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicProxyTest {

    @Test
    void proxyCallMustInvokeHandlerAndPassthroughReturn() {
        Greeter real = new CachedGreeter();
        TraceHandler h = new TraceHandler(real);
        Greeter proxy = (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[]{Greeter.class},
                h);

        String r = proxy.hello("Bei");

        assertThat(h.invokes).isEqualTo(1);            // 代理调用必触发 handler
        assertThat(r).isEqualTo("cache:hi Bei");        // 返回值正确透传
    }

    @Test
    void onlyInterfacesCanBeProxied() {
        // 传入「类」而非「接口」应抛 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
                Proxy.newProxyInstance(
                        String.class.getClassLoader(),
                        new Class<?>[]{String.class},   // String 是类，不是接口
                        (proxy, method, args) -> null));
    }

    @Test
    void recursiveSelfInvocationOverflows() {
        // handler 内调用 proxy 自身方法 → 再次进入 invoke → 无限递归
        InvocationHandler loop = new InvocationHandler() {
            final AtomicReference<Object> ref = new AtomicReference<>();
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                ref.set(proxy);
                // 故意再次调用同一个方法，制造递归
                return method.invoke(ref.get(), args);
            }
        };
        Greeter proxy = (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[]{Greeter.class},
                loop);
        // 期望抛出 Error（栈耗尽）：本 JVM 实际为 NoClassDefFoundError（UndeclaredThrowableException
        // 类初始化恰在栈耗尽时发生），本质是「在 invoke 内调用 proxy 自身方法是致命的」
        assertThrows(Error.class, () -> proxy.hello("x"));
    }

    @Test
    void serviceLoaderDiscoversTwoProviders() {
        ServiceLoader<Greeter> loader = ServiceLoader.load(Greeter.class);
        int count = 0;
        for (Greeter g : loader) {
            assertThat(g).isInstanceOf(Greeter.class);
            count++;
        }
        assertThat(count).isEqualTo(2);  // CachedGreeter + RemoteGreeter
    }
}
