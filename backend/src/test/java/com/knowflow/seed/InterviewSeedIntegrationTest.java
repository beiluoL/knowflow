package com.knowflow.seed;

import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:interview_seed;DB_CLOSE_DELAY=-1;MODE=MySQL")
class InterviewSeedIntegrationTest {
    @Test @org.springframework.transaction.annotation.Transactional void contentRebuildDryRunAndImportAreAppendOnlyAndUserIsolated() throws Exception {
        SysUser owner=user("USER"),other=user("USER");service.activate(owner.getId());
        var before=history();long documents=jdbc.queryForObject("SELECT COUNT(*) FROM doc_document",Long.class);
        mvc.perform(get("/api/learning/seed/java-ai-v1/content-rebuild").header("Authorization",auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.chapters.length()").value(5)).andExpect(jsonPath("$.data.dryRun").value(true));
        assertEquals(documents,jdbc.queryForObject("SELECT COUNT(*) FROM doc_document",Long.class));
        mvc.perform(post("/api/learning/seed/java-ai-v1/content-rebuild").header("Authorization",auth(owner))).andExpect(status().isOk());
        long indices=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);
        mvc.perform(post("/api/learning/seed/java-ai-v1/content-rebuild").header("Authorization",auth(owner))).andExpect(status().isOk());
        assertEquals(indices,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));assertEquals(before,history());
        long chapter=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/concurrency/chapter/concurrency.aqs/chapter'",Long.class,InterviewSeedCatalog.KEY);
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(owner))).andExpect(jsonPath("$.data.revision.version").value("java-ai-content-rebuild-v1")).andExpect(jsonPath("$.data.revision.questions.length()").value(15));
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(other))).andExpect(jsonPath("$.data.revision").doesNotExist());
        long doc=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key='java-ai-content-rebuild-v1' AND resource_key='shared/aqs/document'",Long.class);
        jdbc.update("UPDATE doc_document SET content='保留编辑正文' WHERE id=?",doc);sqlSession.clearCache();
        mvc.perform(post("/api/learning/seed/java-ai-v1/content-rebuild").header("Authorization",auth(owner))).andExpect(status().isOk());
        assertEquals("保留编辑正文",jdbc.queryForObject("SELECT content FROM doc_document WHERE id=?",String.class,doc));
        mvc.perform(post("/api/learning/seed/java-ai-v1/content-rebuild")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/learning/seed/java-ai-v1/content-rebuild").header("Authorization",auth(user("ADMIN")))).andExpect(status().isForbidden());
        jdbc.update("UPDATE doc_document SET deleted=1 WHERE id=?",doc);sqlSession.clearCache();
        mvc.perform(post("/api/learning/seed/java-ai-v1/content-rebuild").header("Authorization",auth(owner))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Test @org.springframework.transaction.annotation.Transactional void readingV5AppendsOneDocumentAndProtectsEditedOldContentAndHistory() throws Exception {
        SysUser owner=user("USER"),other=user("USER");service.activate(owner.getId());
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isOk());
        long oldDoc=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key='java-generics-depth-v1' AND resource_key='shared/document'",Long.class);
        jdbc.update("UPDATE doc_document SET content='用户保留的旧版本' WHERE id=?",oldDoc);
        sqlSession.clearCache(); // Direct fixture SQL bypasses MyBatis' normal write invalidation.
        var before=history();long indices=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isOk());
        assertEquals(indices,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));assertEquals(before,history());
        assertEquals("用户保留的旧版本",jdbc.queryForObject("SELECT content FROM doc_document WHERE id=?",String.class,oldDoc));
        long chapter=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/java-core/chapter/java-core.generics/chapter'",Long.class,InterviewSeedCatalog.KEY);
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(owner))).andExpect(jsonPath("$.data.revision.readingVersion").value("java-generics-reading-v5")).andExpect(jsonPath("$.data.revision.previousReadingDocId").value(oldDoc)).andExpect(jsonPath("$.data.revision.content").value(org.hamcrest.Matchers.containsString("## 03. PECS 原则")));
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(other))).andExpect(jsonPath("$.data.revision").doesNotExist());
        // Simulate a previously activated v1 reader: no v5 selection, edited old source remains active.
        jdbc.update("DELETE FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",GenericsDepthService.KEY,"user/"+owner.getId()+"/reading/"+GenericsDepthService.READING_VERSION);
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(owner))).andExpect(jsonPath("$.data.readingUpgradeAvailable").value(true)).andExpect(jsonPath("$.data.revision.content").value("用户保留的旧版本"));
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isOk());
        assertEquals(indices,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));assertEquals(before,history());
        long selected=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,GenericsDepthService.KEY,"shared/document/"+GenericsDepthService.READING_VERSION);
        jdbc.update("UPDATE doc_document SET content='编辑后的V5' WHERE id=?",selected);
        sqlSession.clearCache();
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision.content").value("编辑后的V5"));
        jdbc.update("UPDATE doc_document SET deleted=1 WHERE id=?",selected);
        sqlSession.clearCache();
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }
    @Test @org.springframework.transaction.annotation.Transactional void pathDetailUsesAuthenticatedUsersPersistedProgress() throws Exception {
        SysUser owner=user("USER"),other=user("USER");service.activate(owner.getId());
        long path=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/java-core/path'",Long.class,InterviewSeedCatalog.KEY);
        long chapter=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/java-core/chapter/java-core.hashmap/chapter'",Long.class,InterviewSeedCatalog.KEY);
        mvc.perform(post("/api/learning/chapters/"+chapter+"/complete").header("Authorization",auth(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/learning/paths/"+path).header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.data.progress").value(25));
        mvc.perform(get("/api/learning/paths/"+path).header("Authorization",auth(other))).andExpect(status().isOk()).andExpect(jsonPath("$.data.enrolled").value(false)).andExpect(jsonPath("$.data.progress").doesNotExist());
    }
    @Test @org.springframework.transaction.annotation.Transactional void genericsRevisionIsExplicitIdempotentAndNeverWritesLearningResults() throws Exception {
        SysUser owner=user("USER"),other=user("USER");service.activate(owner.getId());
        long chapter=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/java-core/chapter/java-core.generics/chapter'",Long.class,InterviewSeedCatalog.KEY);
        String old=jdbc.queryForObject("SELECT content FROM learning_chapter WHERE id=?",String.class,chapter);var before=history();
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("java-generics-depth-v1"));
        long indices=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isOk());
        assertEquals(indices,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));assertEquals(old,jdbc.queryForObject("SELECT content FROM learning_chapter WHERE id=?",String.class,chapter));assertEquals(before,history());
        long card=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key='java-generics-depth-v1' AND resource_key=?",Long.class,"user/"+owner.getId()+"/card/pecs");
        jdbc.update("UPDATE learning_flashcard SET front='my own card',review_count=5 WHERE id=?",card);
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isOk());
        assertEquals("my own card",jdbc.queryForObject("SELECT front FROM learning_flashcard WHERE id=?",String.class,card));assertEquals(5,jdbc.queryForObject("SELECT review_count FROM learning_flashcard WHERE id=?",Integer.class,card));
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(owner))).andExpect(jsonPath("$.data.revision.questions.length()").value(6)).andExpect(jsonPath("$.data.revision.exercises.length()").value(1)).andExpect(jsonPath("$.data.revision.flashcardCount").value(6));
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(other))).andExpect(jsonPath("$.data.revision").doesNotExist());
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(user("ADMIN")))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        jdbc.update("UPDATE resource_knowledge_mapping SET deleted=1 WHERE resource_type='FLASHCARD' AND resource_id=?",card);
        mvc.perform(post("/api/learning/seed/java-ai-v1/generics-depth").header("Authorization",auth(owner))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }
    @Autowired CollectionsDepthService depth;
    @Test @org.springframework.transaction.annotation.Transactional void collectionsRevisionProtectsEditedPrivateRowsAndRejectsDeletedMappings() throws Exception {
        SysUser owner=user("USER");service.activate(owner.getId());depth.activate(owner.getId());
        String prefix="user/"+owner.getId()+"/hashmap/";
        long card=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,CollectionsDepthService.KEY,prefix+"card/hash");
        long note=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,CollectionsDepthService.KEY,prefix+"note");
        jdbc.update("UPDATE learning_flashcard SET front='my edited card',review_count=7,review_interval=14 WHERE id=?",card);
        jdbc.update("UPDATE learning_note SET content='my private note' WHERE id=?",note);
        depth.activate(owner.getId());
        assertEquals("my edited card",jdbc.queryForObject("SELECT front FROM learning_flashcard WHERE id=?",String.class,card));
        assertEquals(7,jdbc.queryForObject("SELECT review_count FROM learning_flashcard WHERE id=?",Integer.class,card));
        assertEquals("my private note",jdbc.queryForObject("SELECT content FROM learning_note WHERE id=?",String.class,note));
        jdbc.update("UPDATE resource_knowledge_mapping SET deleted=1 WHERE resource_type='FLASHCARD' AND resource_id=?",card);
        var error=assertThrows(com.knowflow.exception.BusinessException.class,()->depth.activate(owner.getId()));assertEquals(409,error.getCode());
    }
    @Autowired com.knowflow.mapper.LearningPathMapper pathMapper;
    @Autowired com.knowflow.mapper.LearningChapterMapper chapterMapper;
    @Test void chapterFavoritesAndNotesRejectForeignResourcesAndMismatchedContext() throws Exception {
        SysUser owner=user("USER"),other=user("USER");
        var p=new com.knowflow.entity.LearningPath();p.setTitle("Private");p.setOwnerUserId(owner.getId());p.setStatus(1);pathMapper.insert(p);
        var c=new com.knowflow.entity.LearningChapter();c.setPathId(p.getId());c.setTitle("Private chapter");c.setSortOrder(1);chapterMapper.insert(c);
        mvc.perform(post("/api/learning/chapters/"+c.getId()+"/favorite").header("Authorization",auth(other))).andExpect(status().isForbidden());
        mvc.perform(post("/api/learning/notes").header("Authorization",auth(other)).contentType("application/json").content("{\"title\":\"x\",\"chapterId\":"+c.getId()+",\"pathId\":"+p.getId()+"}")).andExpect(status().isForbidden());
        service.activate(owner.getId());long publicPath=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/java-core/path'",Long.class,InterviewSeedCatalog.KEY);
        mvc.perform(post("/api/learning/notes").header("Authorization",auth(owner)).contentType("application/json").content("{\"title\":\"x\",\"chapterId\":"+c.getId()+",\"pathId\":"+publicPath+"}")).andExpect(status().isBadRequest());
    }
    @Test @org.springframework.transaction.annotation.Transactional void collectionsRevisionIsExplicitIdempotentAndPreservesOriginalHistory() throws Exception {
        SysUser owner=user("USER");service.activate(owner.getId());
        Long chapter=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,InterviewSeedCatalog.KEY,"shared/java-core/chapter/java-core.hashmap/chapter");
        String original=jdbc.queryForObject("SELECT content FROM learning_chapter WHERE id=?",String.class,chapter);
        var before=history();
        mvc.perform(post("/api/learning/seed/java-ai-v1/collections-depth").header("Authorization",auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("java-collections-depth-v1"));
        long indices=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);
        mvc.perform(post("/api/learning/seed/java-ai-v1/collections-depth").header("Authorization",auth(owner))).andExpect(status().isOk());
        assertEquals(indices,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));
        assertEquals(original,jdbc.queryForObject("SELECT content FROM learning_chapter WHERE id=?",String.class,chapter));
        assertEquals(before,history());
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(owner)))
            .andExpect(jsonPath("$.data.revision.exercises.length()").value(2))
            .andExpect(jsonPath("$.data.revision.questions.length()").value(8))
            .andExpect(jsonPath("$.data.revision.notes.length()").value(1));
        SysUser other=user("USER");
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(other)))
            .andExpect(jsonPath("$.data.revision").doesNotExist());
        mvc.perform(post("/api/learning/seed/java-ai-v1/collections-depth").header("Authorization",auth(user("ADMIN")))).andExpect(status().isForbidden());
    }
    @Autowired com.knowflow.service.UserService registration;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;

    @Test void developmentAccountIsRegisteredWithPackOnceAndNoFakeHistory() throws Exception {
        var env=new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("local");env.setProperty("knowflow.seed.development-account.enabled","true");
        var bootstrap=new DevelopmentInterviewAccount(env,jdbc,users,registration,passwords,service);
        var before=history();bootstrap.run(null);
        var login=new com.knowflow.dto.LoginDTO();login.setUsername("seed-personal-v1");login.setPassword("SeedInterview!2026");
        var authenticated=registration.login(login);
        Long actor=authenticated.getUser().getId();
        assertEquals("USER",authenticated.getUser().getRole());
        assertEquals(128,count("learning_flashcard",actor));
        long resources=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);
        bootstrap.run(null);
        assertEquals(resources,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));
        assertEquals(before,history());
        var account=users.selectById(actor);account.setPassword(passwords.encode("DifferentPassword!"));users.updateById(account);
        assertThrows(com.knowflow.exception.BusinessException.class,()->bootstrap.run(null));
        assertTrue(passwords.matches("DifferentPassword!",users.selectById(actor).getPassword()));
    }

    @Test void developmentAccountDoesNothingWhenDisabledOrProductionProfile() throws Exception {
        var env=new org.springframework.mock.env.MockEnvironment();env.setActiveProfiles("local");
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user",Long.class);
        new DevelopmentInterviewAccount(env,jdbc,users,registration,passwords,service).run(null);
        env.setProperty("knowflow.seed.development-account.enabled","true");env.setActiveProfiles("local","prod");
        new DevelopmentInterviewAccount(env,jdbc,users,registration,passwords,service).run(null);
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user",Long.class));
    }
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @Autowired SysUserMapper users;
    @Autowired JdbcTemplate jdbc;
    @Autowired InterviewSeedService service;
    @Autowired ContentRebuildService rebuild;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.knowflow.config.datasource.DynamicRoutingDataSource routing;
    @Autowired InterviewSeedInitializer initializer;

    private SysUser user(String role) {SysUser u=new SysUser();u.setUsername("seed-"+UUID.randomUUID());u.setPassword("fixture-only");u.setRole(role);users.insert(u);return u;}
    private String auth(SysUser u) {return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole());}
    private long count(String table,Long userId) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=? AND deleted=0",Long.class,userId);}
    private Map<String,Long> history() {Map<String,Long> out=new LinkedHashMap<>();for(String table:List.of("learning_event","knowledge_mastery","quiz_answer_record","learning_mistake","wb_review_log","learning_user_chapter","code_submit_record"))out.put(table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));return out;}

    @Test void ordinaryUserCanActivateOriginalContent() throws Exception {
        SysUser user = user("USER");
        String auth="Bearer "+jwt.generateToken(user.getId(),user.getUsername(),"USER");
        mvc.perform(post("/api/learning/seed/java-ai-v1").header("Authorization",auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.activated").value(true))
            .andExpect(jsonPath("$.data.counts.paths").value(8))
            .andExpect(jsonPath("$.data.counts.flashcards").value(128));
    }

    @Test void activationIsIdempotentRelationalAndNeverFabricatesHistory() throws Exception {
        SysUser owner=user("USER"),other=user("USER");Map<String,Long> before=history();
        var activated=service.activate(owner.getId());
        assertEquals(8,((List<?>)activated.get("pathIds")).size());
        assertEquals(128,count("learning_flashcard",owner.getId()));assertEquals(24,count("learning_note",owner.getId()));assertEquals(7,count("learning_task",owner.getId()));assertEquals(8,count("learning_user_path",owner.getId()));assertEquals(1,count("learning_goal",owner.getId()));
        assertEquals(0,count("learning_flashcard",other.getId()));assertEquals(0,count("learning_goal",other.getId()));
        long resources=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);
        var repeated=service.activate(owner.getId());assertEquals(activated,repeated);assertEquals(resources,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));assertEquals(before,history());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=? AND (review_count<>0 OR review_interval<>0 OR last_review_time IS NOT NULL)",Integer.class,owner.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_user_path WHERE user_id=? AND (progress<>0 OR completed_chapters<>0 OR last_study_time IS NOT NULL)",Integer.class,owner.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_task WHERE user_id=? AND status<>0",Integer.class,owner.getId()));
        assertTrue(jdbc.queryForObject("SELECT title FROM learning_task WHERE user_id=? AND deleted=0 ORDER BY create_time DESC LIMIT 1",String.class,owner.getId()).startsWith("Day 1"),"existing task consumer sorts newest first: seed must start sprint at Day1");
        for(String type:List.of("PATH","CHAPTER","KNOWLEDGE","DOC","QUIZ","CODE"))assertEquals(Map.of("PATH",8,"CHAPTER",32,"KNOWLEDGE",96,"DOC",16,"QUIZ",96,"CODE",32).get(type),jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE resource_key LIKE 'shared/%' AND resource_type=?",Integer.class,type));
        for(Long pid:(List<Long>)activated.get("pathIds")) {
            var chapters=jdbc.queryForList("SELECT id,sort_order,doc_ids,flashcard_ids FROM learning_chapter WHERE path_id=? AND deleted=0 ORDER BY sort_order",pid);assertEquals(4,chapters.size());
            for(int i=0;i<4;i++){var ch=chapters.get(i);assertEquals(i+1,((Number)ch.get("sort_order")).intValue(),"existing chapter UI consumes one-based sortOrder as its displayed number");assertTrue(ch.get("flashcard_ids")==null||ch.get("flashcard_ids").toString().isEmpty());long doc=Long.parseLong(ch.get("doc_ids").toString());assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping WHERE resource_type='DOC' AND resource_id=? AND status='ACCEPTED' AND deleted=0",Integer.class,doc));}
        }
        for(var row:jdbc.queryForList("SELECT id FROM learning_flashcard WHERE user_id=?",owner.getId()))assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping WHERE resource_type='FLASHCARD' AND resource_id=? AND deleted=0",Integer.class,row.get("id"))>0);
        for(String[] type:List.of(new String[]{"QUIZ","QUIZ"},new String[]{"CODE","CODE_QUESTION"}))for(var row:jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE resource_type=?",type[0]))assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping WHERE resource_type=? AND resource_id=? AND status='ACCEPTED' AND deleted=0",Integer.class,type[1],row.get("resource_id"))>0);
        Long chapter=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE resource_type='CHAPTER' ORDER BY resource_id LIMIT 1",Long.class);
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.data.questions.length()").value(3)).andExpect(jsonPath("$.data.notes.length()").value(3));
        var bundle=service.chapterResources(owner.getId(),chapter);
        com.knowflow.entity.CodeQuestion exercise=(com.knowflow.entity.CodeQuestion)bundle.get("exercise");
        mvc.perform(get("/api/code-questions/"+exercise.getId()).header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.data.language").value("java"));
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(other))).andExpect(status().isOk()).andExpect(jsonPath("$.data.notes.length()").value(0));
    }

    @Test void anonymousAdminAndDeletedIdentityCannotCreatePrivateState() throws Exception {
        mvc.perform(post("/api/learning/seed/java-ai-v1")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        SysUser admin=user("ADMIN");mvc.perform(post("/api/learning/seed/java-ai-v1").header("Authorization",auth(admin))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));assertEquals(0,count("learning_flashcard",admin.getId()));
        SysUser dead=user("USER");users.deleteById(dead.getId());mvc.perform(post("/api/learning/seed/java-ai-v1").header("Authorization",auth(dead))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test @org.springframework.transaction.annotation.Transactional
    void promotedLearnerKeepsOwnActivatedContentWithoutReinitialization() throws Exception {
        SysUser owner=user("USER"), other=user("USER");service.activate(owner.getId());
        rebuild.activate(owner.getId());var before=history();
        long indices=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);
        owner.setRole("ADMIN");users.updateById(owner);sqlSession.clearCache();
        mvc.perform(get("/api/learning/seed/java-ai-v1").header("Authorization",auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.activated").value(true));
        long chapter=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/java-core/chapter/java-core.hashmap/chapter'",Long.class,InterviewSeedCatalog.KEY);
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision.version").value("java-ai-content-rebuild-v1"));
        mvc.perform(post("/api/learning/seed/java-ai-v1").header("Authorization",auth(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+chapter).header("Authorization",auth(other)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").doesNotExist());
        assertEquals(indices,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));
        assertEquals(before,history());assertEquals(0,count("learning_flashcard",other.getId()));
    }

    @Test void concurrentActivationHasOneInstanceAndSharedTemplate() throws Exception {
        SysUser a=user("USER"),b=user("USER");var pool=Executors.newFixedThreadPool(3);
        try {var work=List.<Callable<Map<String,Object>>>of(()->service.activate(a.getId()),()->service.activate(a.getId()),()->service.activate(b.getId()));for(var f:pool.invokeAll(work))assertEquals(true,f.get(30,TimeUnit.SECONDS).get("activated"));}
        finally{pool.shutdownNow();}
        assertEquals(128,count("learning_flashcard",a.getId()));assertEquals(128,count("learning_flashcard",b.getId()));assertEquals(1,count("learning_goal",a.getId()));assertEquals(288,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE resource_key LIKE 'shared/%'",Integer.class));
    }

    @Test void deletedContentFailsClosedWithoutResurrectionOrPartialState() {
        SysUser user=user("USER");service.activate(user.getId());
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            long goal=count("learning_goal",user.getId());
            jdbc.update("UPDATE learning_flashcard SET deleted=1 WHERE id=(SELECT resource_id FROM interview_seed_resource WHERE resource_key=? AND seed_key=?)","user/"+user.getId()+"/"+InterviewSeedCatalog.text(new InterviewSeedCatalog(json).paths().get(0).get("chapters").get(0),"key")+"/card/0",InterviewSeedCatalog.KEY);
            var e=assertThrows(com.knowflow.exception.BusinessException.class,()->service.activate(user.getId()));assertEquals(409,e.getCode());assertEquals(127,count("learning_flashcard",user.getId()));assertEquals(goal,count("learning_goal",user.getId()));tx.setRollbackOnly();
        });
    }

    @Test void concurrentFirstActivationWorksWithRepeatableReadPoolDefault() throws Exception {
        var cfg=new com.zaxxer.hikari.HikariConfig();cfg.setJdbcUrl("jdbc:h2:mem:seed_rr_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");cfg.setUsername("sa");cfg.setPassword("");cfg.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");cfg.setMaximumPoolSize(5);
        var replacement=new com.zaxxer.hikari.HikariDataSource(cfg);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("db/h2/schema.sql")).execute(replacement);
        var old=routing.swap(replacement,com.knowflow.config.datasource.DbTypeEnum.H2);
        var pool=Executors.newFixedThreadPool(3);
        try {
            initializer.run(null);
            try(var c=replacement.getConnection()){assertEquals(java.sql.Connection.TRANSACTION_REPEATABLE_READ,c.getTransactionIsolation());}
            SysUser a=user("USER"),b=user("USER");
            for(var f:pool.invokeAll(List.<Callable<Map<String,Object>>>of(()->service.activate(a.getId()),()->service.activate(a.getId()),()->service.activate(b.getId()))))assertEquals(true,f.get(30,TimeUnit.SECONDS).get("activated"));
            assertEquals(128,count("learning_flashcard",a.getId()));assertEquals(128,count("learning_flashcard",b.getId()));assertEquals(288,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE resource_key LIKE 'shared/%'",Integer.class));
        } finally {pool.shutdownNow();routing.swap(old,com.knowflow.config.datasource.DbTypeEnum.H2);replacement.close();}
    }

    @Test void missingPrivateIndexOrKnowledgeMappingAndDeletedResourceAreConsistentConflicts() {
        SysUser owner=user("USER");service.activate(owner.getId());
        for(String damage:List.of("index","mapping","deleted"))new TransactionTemplate(transactions).executeWithoutResult(tx->{
            var card=jdbc.queryForMap("SELECT resource_key,resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key LIKE ? AND resource_type='FLASHCARD' ORDER BY resource_id LIMIT 1",InterviewSeedCatalog.KEY,"user/"+owner.getId()+"/%");
            if(damage.equals("index"))jdbc.update("DELETE FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",InterviewSeedCatalog.KEY,card.get("resource_key"));
            if(damage.equals("mapping"))jdbc.update("UPDATE resource_knowledge_mapping SET deleted=1 WHERE resource_type='FLASHCARD' AND resource_id=?",card.get("resource_id"));
            if(damage.equals("deleted"))jdbc.update("UPDATE learning_flashcard SET deleted=1 WHERE id=?",card.get("resource_id"));
            assertEquals(409,assertThrows(com.knowflow.exception.BusinessException.class,()->service.status(owner.getId()),damage+" status must not report activated-ready").getCode());
            assertEquals(409,assertThrows(com.knowflow.exception.BusinessException.class,()->service.activate(owner.getId())).getCode());
            tx.setRollbackOnly();
        });
    }
}
