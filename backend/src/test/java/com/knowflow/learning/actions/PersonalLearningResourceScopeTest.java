package com.knowflow.learning.actions;
import com.knowflow.entity.*;import com.knowflow.mapper.DocCategoryMapper;import com.knowflow.security.DocumentAccessPolicy;import com.knowflow.service.KbMemberService;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class PersonalLearningResourceScopeTest {
 DocCategoryMapper categories;DocumentAccessPolicy access;KbMemberService members;PersonalLearningResourceScope scope;
 @BeforeEach void setup(){categories=mock(DocCategoryMapper.class);access=mock(DocumentAccessPolicy.class);members=mock(KbMemberService.class);scope=new PersonalLearningResourceScope(access,categories,members);}
 DocCategory category(Long owner){var c=new DocCategory();c.setId(1L);c.setOwnerId(owner);c.setStatus(1);return c;}
 @Test void adminGlobalReadDoesNotPutAnotherPersonsUnsharedPrivateContentInPlan(){when(access.canReadCategory(1L,3L)).thenReturn(true);when(categories.selectById(1L)).thenReturn(category(2L));assertFalse(scope.category(1L,3L));}
 @Test void ownerAndPublicResourcesRemainAvailable(){when(access.canReadCategory(1L,2L)).thenReturn(true);when(categories.selectById(1L)).thenReturn(category(2L));assertTrue(scope.category(1L,2L));when(access.canReadCategory(1L,null)).thenReturn(true);when(access.canReadCategory(1L,4L)).thenReturn(true);assertTrue(scope.category(1L,4L));}
 @Test void explicitReaderAndEditorMembershipAllowsSharedContent(){when(access.canReadCategory(1L,3L)).thenReturn(true);when(categories.selectById(1L)).thenReturn(category(2L));for(String role:new String[]{"READER","EDITOR"}){var m=new KbMember();m.setStatus(1);m.setRole(role);when(members.getMember(1L,3L)).thenReturn(m);assertTrue(scope.category(1L,3L));}}
 @Test void removedMembershipDoesNotGrantPersonalScope(){when(access.canReadCategory(1L,3L)).thenReturn(true);when(categories.selectById(1L)).thenReturn(category(2L));var m=new KbMember();m.setRole("READER");m.setStatus(0);when(members.getMember(1L,3L)).thenReturn(m);assertFalse(scope.category(1L,3L));}
}
