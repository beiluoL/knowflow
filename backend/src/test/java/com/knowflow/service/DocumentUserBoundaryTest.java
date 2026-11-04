package com.knowflow.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DocumentUserBoundaryTest {
    @Test
    void documentServiceCannotInjectUserPersistence() {
        for(var field : com.knowflow.service.impl.DocServiceImpl.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().equals("com.knowflow.mapper.SysUserMapper"));
        }
    }
}
