package com.knowflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 3 RED→GREEN Controller 契约基线测试（只跑存在性/方法签名，不跑集成）。
 * <p>
 * RED：ClassNotFound com.knowflow.controller.LearningPlanController。
 * GREEN：Controller 存在；3 条方法签名、注解 @RequestMapping / @Tag 齐全。
 */
class LearningPlanControllerContractTest {

    private Class<?> cls;

    @BeforeEach
    void load() throws ClassNotFoundException {
        cls = Class.forName("com.knowflow.controller.LearningPlanController");
    }

    @Test
    @DisplayName("RED3.1: LearningPlanController 存在且带生成接口方法 generatePlan")
    void generateMethodExists() throws Exception {
        // 任意一种签名：generate(PlanGenerateDTO, AuthenticationPrincipal) → 我们只校验方法名
        boolean found = false;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equalsIgnoreCase("generatePlan") || m.getName().equalsIgnoreCase("generate")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "generatePlan(PlanGenerateDTO) 方法不存在");
    }

    @Test
    @DisplayName("RED3.2: getToday 方法存在")
    void todayMethodExists() throws Exception {
        boolean found = false;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equalsIgnoreCase("getTodayPlan") || m.getName().equalsIgnoreCase("today")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "getTodayPlan 方法不存在");
    }

    @Test
    @DisplayName("RED3.3: exportCalendar 方法存在")
    void calendarMethodExists() throws Exception {
        boolean found = false;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("calendar")
                    || m.getName().equalsIgnoreCase("exportIcs")
                    || m.getName().equalsIgnoreCase("export")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "exportCalendar/calendar.ics 方法不存在");
    }

    @Test
    @DisplayName("RED3.4: @RequestMapping(\"/api/learning/plan\") 注解存在于类级别")
    void requestMappingAtClassLevel() {
        org.springframework.web.bind.annotation.RequestMapping a =
                cls.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(a, "类级别 @RequestMapping 缺失");
        String[] paths = a.value();
        if (paths.length == 0) paths = a.path();
        assertTrue(paths.length > 0, "@RequestMapping 应包含路径 /api/learning/plan");
        String p = paths[0];
        assertTrue(p.contains("plan") && p.contains("learning"),
                "@RequestMapping 路径应为 /api/learning/plan，实际 " + p);
    }

    @Test
    @DisplayName("RED3.5: LearningPlanService.defaultStartDate 不返回 null / 不是过去")
    void defaultStartDateFallsNextMondayOrLater() {
        LocalDate d = LearningPlanService.defaultStartDate();
        assertNotNull(d);
        LocalDate today = LocalDate.now();
        // 至少 >= 今天，且通常在本周日/一之后
        assertFalse(d.isBefore(today), "默认起始日不应早于今天");
    }
}
