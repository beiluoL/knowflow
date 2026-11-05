package com.knowflow.service;

import com.knowflow.identity.service.UserService;


import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.identity.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 验证文档用户计数更新的事务与归属校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:document_user_boundary;DB_CLOSE_DELAY=-1;MODE=MySQL")
class DocumentUserCounterTransactionTest {
    @Autowired MockMvc http;
    @SpyBean SysUserMapper users;
    @Autowired DocDocumentMapper docs;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtils tokens;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserService owner;

    private SysUser newUser() {
        SysUser user = new SysUser();
        user.setUsername("boundary-" + java.util.UUID.randomUUID());
        user.setPassword(""); user.setRole("USER"); user.setFavoriteCount(0); user.setReadDocsCount(0);
        users.insert(user); return user;
    }

    @Test
    void ownerFailureRollsBackDocumentRelationAndBothCounters() throws Exception {
        SysUser user = newUser();
        DocDocument document = new DocDocument(); document.setTitle("atomic favorite"); document.setContent("fixture"); document.setStatus(1); docs.insert(document);
        doThrow(new com.knowflow.exception.BusinessException(409,"injected counter failure")).when(users).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        try {
            http.perform(post("/api/docs/" + document.getId() + "/favorite")
                    .header("Authorization","Bearer " + tokens.generateToken(user.getId(),user.getUsername(),"USER")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM doc_favorite WHERE user_id=? AND doc_id=?",Integer.class,user.getId(),document.getId()));
            assertEquals(0, users.selectById(user.getId()).getFavoriteCount());
            assertEquals(0, docs.selectById(document.getId()).getFavoriteCount());
        } finally { reset(users); }
    }

    @Test
    void ownerCommandCannotModifyAnotherUserOrRunWithoutBusinessTransaction() {
        SysUser caller = newUser(); SysUser other = newUser();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(caller.getId(),"",List.of()));
        try {
            assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> owner.recordDocumentFavoriteAdded(caller.getId()));
            var failure = assertThrows(com.knowflow.exception.BusinessException.class,
                    () -> new TransactionTemplate(transactions).execute(status -> { owner.recordDocumentFavoriteAdded(other.getId()); return null; }));
            assertEquals(403,failure.getCode());
            assertEquals(0, users.selectById(other.getId()).getFavoriteCount());
            new TransactionTemplate(transactions).execute(status -> { owner.recordDocumentReadCompleted(caller.getId()); return null; });
            assertEquals(1,users.selectById(caller.getId()).getReadDocsCount());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
