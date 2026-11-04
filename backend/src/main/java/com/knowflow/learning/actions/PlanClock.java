package com.knowflow.learning.actions;

import java.time.Instant;
import org.springframework.stereotype.Component;

/** 计划调度用的可测试时钟封装，统一以 Instant 提供“当前时间”，便于计划时钟逻辑在测试中可控。 */
@Component
public class PlanClock {
    public Instant now() {
        return Instant.now();
    }
}
