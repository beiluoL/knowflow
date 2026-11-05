package com.knowflow.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证文档操作的用户归属边界。
 */
class DocumentUserBoundaryTest {
    @Test
    void documentServiceCannotInjectUserPersistence() {
        for(var field : com.knowflow.knowledge.service.impl.DocServiceImpl.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().equals("com.knowflow.identity.mapper.SysUserMapper"));
        }
    }
}
