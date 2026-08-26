package com.knowflow.service;

import com.knowflow.dto.PlanGenerateDTO;
import com.knowflow.entity.LearningPlan;
import com.knowflow.mapper.LearningPlanMapper;
import com.knowflow.vo.LearningPlanVO;
import com.knowflow.vo.PlanBlockVO;
import com.knowflow.vo.PlanItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1 RED→GREEN 基线测试。
 * 先以反射方式验证实体 / VO / DTO / Mapper 的存在性与字段契约，
 * 保证 Task1 产出的基础骨架符合 spec §FR2、§NFR1。
 */
class LearningPlanBaselineTest {

    // ----- 基础类型存在性 -----

    @Test
    @DisplayName("RED1: LearningPlan 实体存在且含核心字段")
    void learningPlanEntityStructure() throws Exception {
        Class<?> cls = Class.forName("com.knowflow.entity.LearningPlan");
        assertField(cls, "userId", Long.class);
        assertField(cls, "planDate", LocalDate.class);
        assertField(cls, "timeBlocks", String.class);   // JSON array
        assertField(cls, "learningTaskIds", String.class);
        assertField(cls, "habitIds", String.class);
        assertField(cls, "todoIds", String.class);
        assertField(cls, "completedRatio", BigDecimal.class);
        assertField(cls, "status", Integer.class);
    }

    @Test
    @DisplayName("RED2: LearningPlanMapper 存在")
    void learningPlanMapperExists() throws Exception {
        Class<?> cls = Class.forName("com.knowflow.mapper.LearningPlanMapper");
        assertTrue(org.apache.ibatis.annotations.Mapper.class.isAssignableFrom(cls)
                        || cls.isInterface(),
                "Mapper 应为接口");
    }

    @Test
    @DisplayName("RED3: VO 嵌套结构 LearningPlanVO(blocks) / PlanBlockVO(items) / PlanItemVO")
    void voNestedStructure() throws Exception {
        Class<?> voCls = Class.forName("com.knowflow.vo.LearningPlanVO");
        assertField(voCls, "blocks", List.class);
        assertField(voCls, "completedRatio", BigDecimal.class);

        Class<?> blockCls = Class.forName("com.knowflow.vo.PlanBlockVO");
        assertField(blockCls, "items", List.class);
        assertField(blockCls, "timeSlot", String.class);

        Class<?> itemCls = Class.forName("com.knowflow.vo.PlanItemVO");
        assertField(itemCls, "type", String.class);
        assertField(itemCls, "title", String.class);
        assertField(itemCls, "completed", Boolean.class);
        assertField(itemCls, "duration", Integer.class);
    }

    @Test
    @DisplayName("RED4: PlanGenerateDTO 入参 startDate/force 存在")
    void planGenerateDtoFields() throws Exception {
        Class<?> cls = Class.forName("com.knowflow.dto.PlanGenerateDTO");
        assertField(cls, "startDate", LocalDate.class);
        assertField(cls, "force", Boolean.class);
    }

    // ----- 实例化 / setter/getter 基础 -----

    @Test
    @DisplayName("RED5: LearningPlan 可被实例化 + setPlanDate 正常")
    void learningPlanInstantiable() throws Exception {
        LearningPlan plan = new LearningPlan();
        plan.setPlanDate(LocalDate.of(2026, 8, 27));
        plan.setCompletedRatio(BigDecimal.valueOf(33.33));
        plan.setStatus(1);
        assertEquals(LocalDate.of(2026, 8, 27), plan.getPlanDate());
        assertEquals(0, BigDecimal.valueOf(33.33).compareTo(plan.getCompletedRatio()));
        assertEquals(1, plan.getStatus());
    }

    @Test
    @DisplayName("RED6: PlanItemVO 三种 type 字段齐全")
    void planItemVoTypeFields() throws Exception {
        Class<?> cls = PlanItemVO.class;
        // 三类关联 ID 字段（learningTaskId/taskId/habitId）至少有对应 setter
        Field id = cls.getDeclaredField("learningTaskId");
        assertNotNull(id);
        Field tid = cls.getDeclaredField("taskId");
        assertNotNull(tid);
        Field hid = cls.getDeclaredField("habitId");
        assertNotNull(hid);
    }

    // 私有辅助
    private void assertField(Class<?> cls, String name, Class<?> expectedType) throws Exception {
        Field f = cls.getDeclaredField(name);
        if (expectedType.isAssignableFrom(f.getType())) return;
        // 兼容 primitive wrapper（例如 boolean vs Boolean）
        if (expectedType == Boolean.class && (f.getType() == boolean.class)) return;
        if (expectedType == Integer.class && (f.getType() == int.class)) return;
        fail(cls.getSimpleName() + "." + name + " 类型应为 " + expectedType.getSimpleName()
                + "，实际为 " + f.getType().getSimpleName());
    }
}
