package com.knowflow.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiSuggestionLayeringTest {
    @Test
    void controllerHasNoPersistenceDependencies() {
        for (var field : AiLearningSuggestionController.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(type.contains(".mapper.") || type.contains("JdbcTemplate"), type);
        }
    }
}
