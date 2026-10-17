package com.knowflow.interview.verify.dynamicproxy;

/** SPI 实现 B。 */
public class RemoteGreeter implements Greeter {
    @Override
    public String hello(String name) {
        return "remote:hi " + name;
    }
}
