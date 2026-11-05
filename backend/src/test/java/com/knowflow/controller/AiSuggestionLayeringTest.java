package com.knowflow.controller;

import com.knowflow.learning.controller.AiLearningSuggestionController;


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证 AI 建议控制器不直接依赖持久化层。
 */
class AiSuggestionLayeringTest {
    @Test
    void controllerHasNoPersistenceDependencies() {
        for (var field : AiLearningSuggestionController.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(type.contains(".mapper.") || type.contains("JdbcTemplate"), type);
        }
    }
}
