package com.knowflow.interview.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.TaskDTO;
import com.knowflow.entity.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.service.MockInterviewService;
import com.knowflow.mapper.*;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.service.TaskService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewBridgeTest {
    JdbcTemplate jdbc;
    MockInterviewService sessions;
    KgEntityMapper entities;
    DocumentAccessPolicy access;
    TaskService tasks;
    InterviewLearningBridge bridge;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE mock_interview_v2_state(session_id BIGINT PRIMARY KEY,user_id BIGINT,state VARCHAR(30))");
        jdbc.execute("CREATE TABLE mock_interview_v2_turn(id BIGINT,session_id BIGINT,user_id BIGINT,evaluation_json VARCHAR(4000))");
        jdbc.execute("CREATE TABLE interview_remediation(session_id BIGINT,knowledge_id BIGINT,user_id BIGINT,task_id BIGINT,request_id VARCHAR(100),PRIMARY KEY(session_id,knowledge_id))");
        jdbc.update("INSERT INTO mock_interview_v2_state VALUES(1,7,'COMPLETED')");
        jdbc.update("INSERT INTO mock_interview_v2_turn VALUES(11,1,7,?)","{\"gaps\":[\"Transactions\",\"made up\"]}");
        sessions=mock(MockInterviewService.class); entities=mock(KgEntityMapper.class); access=mock(DocumentAccessPolicy.class);tasks=mock(TaskService.class);
        var entity=new KgEntity();entity.setId(3L);entity.setName("Transactions");
        when(entities.selectList(any())).thenReturn(List.of(entity));when(access.canReadKnowledge(entity,7L)).thenReturn(true);
        when(tasks.createTask(eq(7L),any(TaskDTO.class))).thenReturn(99L);
        bridge=new InterviewLearningBridge(jdbc,sessions,entities,access,tasks,new ObjectMapper());
    }
    @Test void matchesOnlyExactReadablePersistedGap() {
        var result=bridge.preview(7L,1L);assertEquals(1,result.resources().size());assertEquals(3L,result.resources().get(0).knowledgeId());
        when(access.canReadKnowledge(any(),eq(7L))).thenReturn(false);
        assertTrue(bridge.preview(7L,1L).resources().isEmpty());
        verifyNoInteractions(tasks);
    }
    @Test void deduplicatesAcrossRequestIdsAndRejectsInventedIds() {
        assertEquals(99L,bridge.confirm(7L,1L,new InterviewLearningBridge.ConfirmRequest("first",List.of(3L,3L))).get(0).taskId());
        assertEquals(99L,bridge.confirm(7L,1L,new InterviewLearningBridge.ConfirmRequest("second",List.of(3L))).get(0).taskId());
        verify(tasks,times(1)).createTask(eq(7L),any());
        assertThrows(BusinessException.class,()->bridge.confirm(7L,1L,new InterviewLearningBridge.ConfirmRequest("third",List.of(88L))));
    }
    @Test void anonymousOtherUserAndAdminCannotBypassSessionOwnership() {
        assertThrows(BusinessException.class,()->bridge.preview(null,1L));
        for(long actor : List.of(8L,99L)) {
            when(sessions.getOwn(actor,1L)).thenThrow(new BusinessException(404,"不存在"));
            assertThrows(BusinessException.class,()->bridge.preview(actor,1L));
        }
        verifyNoInteractions(tasks);
    }
    @Test void requiresCompletedAndMissingResourcesRemainEmpty() {
        jdbc.update("UPDATE mock_interview_v2_state SET state='PAUSED'");
        assertThrows(BusinessException.class,()->bridge.preview(7L,1L));
        jdbc.update("UPDATE mock_interview_v2_state SET state='COMPLETED'");
        when(entities.selectList(any())).thenReturn(List.of());
        assertTrue(bridge.preview(7L,1L).resources().isEmpty());
    }
    @Test void actualPolicyAllowsOwnerEditorReaderAdminAndRejectsOther() {
        var categories=mock(DocCategoryMapper.class);
        var members=mock(com.knowflow.service.KbMemberService.class);
        var docs=mock(DocDocumentMapper.class);
        var policy=new DocumentAccessPolicy(categories,members,docs);
        var category=new DocCategory();category.setId(20L);category.setOwnerId(7L);category.setStatus(1);
        when(categories.selectById(20L)).thenReturn(category);
        var membership=new com.knowflow.entity.KbMember();
        when(members.listByCategory(20L)).thenReturn(List.of(membership));
        when(members.canView(20L,8L)).thenReturn(true); // EDITOR
        when(members.canView(20L,9L)).thenReturn(true); // READER
        when(members.canViewAll(99L)).thenReturn(true);
        var entity=new KgEntity();entity.setId(3L);entity.setName("Transactions");entity.setCategoryId(20L);
        for(long actor : List.of(7L,8L,9L,99L)) assertTrue(policy.canReadKnowledge(entity,actor));
        assertFalse(policy.canReadKnowledge(entity,10L));assertFalse(policy.canReadKnowledge(entity,null));
        when(entities.selectList(any())).thenReturn(List.of(entity));
        bridge=new InterviewLearningBridge(jdbc,sessions,entities,policy,tasks,new ObjectMapper());
        assertEquals(1,bridge.preview(7L,1L).resources().size());
    }
    @Test void contextBoundsAndDelegatesEveryDocumentAuthorization() {
        DocDocumentMapper docs=mock(DocDocumentMapper.class);
        DocDocument doc=new DocDocument();doc.setId(4L);doc.setTitle("Project");doc.setContent("x".repeat(3000));when(docs.selectById(4L)).thenReturn(doc);
        var context=new InterviewProjectContextService(docs,access);
        var snapshot=context.resolve(7L,List.of(4L,4L),"user claim");
        assertEquals("USER_REPORTED",snapshot.userDescriptionSource());assertEquals(2400,snapshot.evidence().get(0).quote().length());
        assertEquals("DOCUMENT_CONTENT_ONLY",snapshot.evidence().get(0).claimScope());verify(access).requireRead(doc,7L);
        doThrow(new BusinessException(403,"无权")).when(access).requireRead(doc,8L);
        assertThrows(BusinessException.class,()->context.resolve(8L,List.of(4L),""));
        assertThrows(BusinessException.class,()->context.resolve(null,List.of(),""));
        assertTrue(context.resolve(7L,List.of(),"").evidence().isEmpty());
    }
}
