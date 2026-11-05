package com.knowflow.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.KnowFlowApplication;
import java.util.HashSet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 Spring、MyBatis 检查所有迁移后的生产类型注册。 */
@SpringBootTest(classes = KnowFlowApplication.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "knowflow.datasource.type=h2",
        "knowflow.datasource.h2.url=jdbc:h2:mem:feature_packages;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class FeaturePackageRegistrationTest {
    @Autowired ConfigurableApplicationContext context;
    @Autowired SqlSessionFactory sessions;

    @Test
    void movedBeansAndMapperNamespacesRemainUnique() throws Exception {
        var names = new HashSet<String>();
        int count = 0;
        for (String manifest : new String[]{"PACKAGE_RELOCATION_2026-11-05.json",
                "PACKAGE_RELOCATION_REMAINDER_2026-11-05.json"}) {
            var rows = new ObjectMapper().readTree(java.util.Objects.requireNonNull(
                    getClass().getResourceAsStream("/architecture/" + manifest)));
            for (var row : rows) {
                String name = row.path("newType").asText();
                assertTrue(names.add(name), name);
                Class<?> type = Class.forName(name);
                assertThrows(ClassNotFoundException.class, () -> Class.forName(row.path("oldType").asText()));
                if (type.isAnnotationPresent(Mapper.class)) {
                    assertEquals(1, context.getBeansOfType(type).size(), name);
                    assertTrue(sessions.getConfiguration().hasMapper(type), name);
                    assertTrue(sessions.getConfiguration().hasStatement(name + ".selectById"), name);
                } else if (org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(type, Component.class)) {
                    var outcome = ConditionEvaluationReport.get(context.getBeanFactory())
                            .getConditionAndOutcomesBySource().get(name);
                    int expected = outcome != null && !outcome.isFullMatch() ? 0 : 1;
                    assertEquals(expected, context.getBeansOfType(type).size(), name);
                }
                count++;
            }
        }
        assertEquals(610, count);
    }
}
