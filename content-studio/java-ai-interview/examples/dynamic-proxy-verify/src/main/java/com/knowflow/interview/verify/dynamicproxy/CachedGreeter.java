package com.knowflow.interview.verify.dynamicproxy;

/** SPI 实现 A。 */
public class CachedGreeter implements Greeter {
    @Override
    public String hello(String name) {
        return "cache:hi " + name;
    }
}
