package com.knowflow.controller;

import com.knowflow.identity.controller.UserSearchController;


import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

/**
 * 验证用户搜索结果不泄露邮箱。
 */
class UserSearchPrivacyTest {
    @Test
    void searchNeverProjectsEmail() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "privacy"), SysUser.class);
        SysUserMapper mapper = mock(SysUserMapper.class);
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("public-name");
        user.setEmail("private@example.com");
        user.setPassword("secret");
        when(mapper.selectList(any())).thenReturn(List.of(user));
        var result = new UserSearchController(new com.knowflow.identity.service.UserSearchService(mapper)).search("public", null);
        assertNull(result.getData().get(0).getEmail());
    }
}
