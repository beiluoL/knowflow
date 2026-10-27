package com.knowflow.service;

import com.knowflow.dto.*;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.security.HostCapabilityProperties;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:oct2_import;DB_CLOSE_DELAY=-1;MODE=MySQL")
class ImportPositiveChainTest {
    @TempDir Path temp;
    @Autowired ObsidianImportService obsidian;
    @Autowired KnowledgeImportService importer;
    @Autowired HostCapabilityProperties host;
    @Autowired DocCategoryMapper categories;
    @Autowired KbMemberService members;
    @Autowired LearningPathMapper paths;
    @Autowired SysUserMapper users;
    @Autowired DocDocumentMapper docs;
    @Test void incrementalDeduplicationStaysWithinTarget() {
        host.setEnabled(false);DocCategory a=new DocCategory();a.setName("private-a");a.setOwnerId(3L);a.setParentId(0L);a.setStatus(1);categories.insert(a);
        DocCategory b=new DocCategory();b.setName("private-b");b.setOwnerId(2L);b.setParentId(0L);b.setStatus(1);categories.insert(b);
        var file=new MockMultipartFile("files","same.md","text/markdown","# Same\nOffline.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        KnowledgeImportOptionsDTO options=new KnowledgeImportOptionsDTO();options.setCreateSubCategories(false);options.setTargetCategoryId(a.getId());
        importer.importDirectoryWithProgress(new org.springframework.web.multipart.MultipartFile[]{file},options,3L,UUID.randomUUID().toString(),null);
        options.setTargetCategoryId(b.getId());var result=importer.importDirectoryWithProgress(new org.springframework.web.multipart.MultipartFile[]{file},options,2L,UUID.randomUUID().toString(),null);
        assertEquals(1,result.getSuccessCount());assertEquals(0,result.getSkippedCount());
    }
    @Test void importingCannotCreateDescendantAcrossIndependentOwner() {
        DocCategory root=new DocCategory();root.setName("root");root.setOwnerId(2L);root.setParentId(0L);root.setStatus(1);categories.insert(root);
        DocCategory child=new DocCategory();child.setName("private");child.setOwnerId(3L);child.setParentId(root.getId());child.setStatus(1);categories.insert(child);
        KnowledgeImportOptionsDTO options=new KnowledgeImportOptionsDTO();options.setTargetCategoryId(root.getId());options.setCreateSubCategories(true);
        var file=new MockMultipartFile("files","private/new/safe.md","text/markdown","# Safe\nOffline.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result=importer.importDirectoryWithProgress(new org.springframework.web.multipart.MultipartFile[]{file},options,2L,UUID.randomUUID().toString(),null);
        assertEquals(0,result.getSuccessCount());assertEquals(1,result.getFailedCount());
        assertEquals(0,categories.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocCategory>().eq(DocCategory::getParentId,child.getId())));
    }
    @MockBean EmbeddingService embeddings;
    @BeforeEach void actor(){ host.setEnabled(true);host.setAuthorizedUserIds(List.of(2L));host.setAllowedRoots(List.of(temp.toString()));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(2L,"unused",List.of())); }
    @AfterEach void clear(){SecurityContextHolder.clearContext();host.setEnabled(false);}
    @Test void newKnowledgeBaseAndFourModulesComplete() throws Exception {
        Path file=Files.writeString(temp.resolve("safe.md"),"# HashMap\n\n## Definition\nHashMap stores key value pairs.\n\n## Usage\nUse put to insert and get to read values.");
        ObsidianImportDTO dto=new ObsidianImportDTO();dto.setFilePaths(List.of(file.toString()));dto.setModules(List.of("knowledge","path","flashcard","quiz"));
        var result=obsidian.importAll(dto,2L);
        assertEquals(1,result.getDocCount());assertEquals(2L,categories.selectById(result.getCategoryId()).getOwnerId());assertTrue(members.canEditDocs(result.getCategoryId(),2L));
        assertNotNull(result.getLearningPathId());assertEquals(2L,paths.selectById(result.getLearningPathId()).getOwnerUserId());assertTrue(result.getFlashcardCount()>0);assertTrue(result.getQuizCount()>0);assertEquals(4,result.getGeneratedModules().size());
    }
    @Test void directoryPreprocessingAndFourModulesComplete() throws Exception {
        Path chapter=Files.createDirectory(temp.resolve("chapter"));Files.writeString(chapter.resolve("safe.md"),"# Safe directory\n\n## Definition\nOffline local import fixture.\n\n## Usage\nCapture then review.");
        ObsidianImportDTO dto=new ObsidianImportDTO();dto.setPath(temp.toString());dto.setModules(List.of("knowledge","path","flashcard","quiz"));
        var result=obsidian.importAll(dto,2L);assertEquals(1,result.getDocCount());assertTrue(result.getFlashcardCount()>0);assertTrue(result.getQuizCount()>0);assertNotNull(result.getLearningPathId());
    }
    @Test void ordinaryMultipartImportWorksWithHostDisabled() {
        host.setEnabled(false);DocCategory category=new DocCategory();category.setName("multipart-fixture");category.setParentId(0L);category.setStatus(1);category.setOwnerId(2L);categories.insert(category);members.addMember(category.getId(),2L,KbMember.ROLE_OWNER,null);
        KnowledgeImportOptionsDTO options=new KnowledgeImportOptionsDTO();options.setTargetCategoryId(category.getId());options.setCreateSubCategories(false);
        var file=new MockMultipartFile("files","safe.md","text/markdown","# Safe\n\nOffline fixture.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result=importer.importDirectoryWithProgress(new org.springframework.web.multipart.MultipartFile[]{file},options,2L,UUID.randomUUID().toString(),null);
        assertEquals(1,result.getSuccessCount());
    }
    @Test void editorImportDoesNotTakeOwnershipOfChildCategory() {
        SysUser editor=new SysUser();editor.setUsername("import-editor-"+UUID.randomUUID());editor.setPassword("unused");editor.setRole("USER");users.insert(editor);
        DocCategory root=new DocCategory();root.setName("owner-root");root.setOwnerId(2L);root.setParentId(0L);root.setStatus(1);categories.insert(root);members.addMember(root.getId(),2L,"OWNER",null);members.addMember(root.getId(),editor.getId(),"EDITOR",null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(editor.getId(),"unused",List.of()));host.setEnabled(false);
        KnowledgeImportOptionsDTO options=new KnowledgeImportOptionsDTO();options.setTargetCategoryId(root.getId());
        var file=new MockMultipartFile("files","chapter/safe.md","text/markdown","# Safe\n\nOffline fixture.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1,importer.importDirectoryWithProgress(new org.springframework.web.multipart.MultipartFile[]{file},options,editor.getId(),UUID.randomUUID().toString(),null).getSuccessCount());
        var child=categories.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocCategory>().eq(DocCategory::getParentId,root.getId()));assertNotNull(child);assertEquals(2L,child.getOwnerId());
    }
}
