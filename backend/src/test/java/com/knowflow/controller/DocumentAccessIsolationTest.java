package com.knowflow.controller;

import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.service.*;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:oct2_docs;DB_CLOSE_DELAY=-1;MODE=MySQL")
class DocumentAccessIsolationTest {
    @Autowired MockMvc http; @Autowired JwtUtils jwt; @Autowired DocCategoryMapper categories;
    @Autowired DocDocumentMapper docs; @Autowired KbMemberService members; @Autowired SysUserMapper users;
    @Autowired JdbcTemplate sql;
    @Autowired LearningPathMapper paths; @Autowired LearningChapterMapper chapters;
    @Autowired DocChunkMapper chunks; @Autowired DocChunkService retrieval;
    @Autowired QuizQuestionMapper questions;
    @Autowired KgEntityMapper entities;
    @Autowired DocService documentService;
    @Test void cyclicAndDeletedAncestorsFailClosed() throws Exception {
        Long a=category(null,0L);Long b=category(null,a);sql.update("update doc_category set parent_id=? where id=?",b,a);Long cycleDoc=doc(a);
        http.perform(get("/api/docs/"+cycleDoc).header("Authorization",auth(other))).andExpect(status().isForbidden());
        Long child=category(null,category);Long inheritedDoc=doc(child);sql.update("update doc_category set deleted=1 where id=?",category);
        http.perform(get("/api/docs/"+inheritedDoc).header("Authorization",auth(reader))).andExpect(status().isForbidden());
    }
    @Test void mixedUnauthorizedBatchHasNoPartialMutation() {
        Long privateOther=doc(category(other,0L));Long target=category(owner,0L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(owner,"unused",List.of()));
        try {
            assertThrows(com.knowflow.exception.BusinessException.class,()->documentService.batchMoveDocs(List.of(document,privateOther),target));
            assertEquals(category,docs.selectById(document).getCategoryId());assertNotEquals(target,docs.selectById(privateOther).getCategoryId());
            assertThrows(com.knowflow.exception.BusinessException.class,()->documentService.batchDeleteDocs(List.of(document,privateOther)));
            assertNotNull(docs.selectById(document));assertNotNull(docs.selectById(privateOther));
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void removedPrivateAssetReferenceRemainsProtected() throws Exception {
        String url="/uploads/retained-"+UUID.randomUUID()+".png";sql.update("update doc_document set content=? where id=?","![private]("+url+")",document);
        http.perform(put("/api/docs/"+document).header("Authorization",auth(owner)).contentType("application/json").content("{\"content\":\"removed image\"}")).andExpect(status().isOk());
        http.perform(get(url)).andExpect(status().isUnauthorized());http.perform(get(url).header("Authorization",auth(other))).andExpect(status().isForbidden());
    }
    @Test void privateReferenceCannotRevokePublicAssetAccess() throws Exception {
        String url="/uploads/public-"+UUID.randomUUID()+".png";Long publicDoc=doc(null);sql.update("update doc_document set content=? where id=?","![public]("+url+")",publicDoc);
        http.perform(put("/api/docs/"+document).header("Authorization",auth(owner)).contentType("application/json").content("{\"content\":\"![clone]("+url+")\"}")).andExpect(status().isOk());
        assertNotEquals(403,http.perform(get(url)).andReturn().getResponse().getStatus());
        assertNotEquals(401,http.perform(get(url)).andReturn().getResponse().getStatus());
    }
    @org.springframework.boot.test.mock.mockito.MockBean EmbeddingService embeddings;
    Long owner,editor,reader,other,category,document;
    String keyword;
    @BeforeEach void fixture(){
        owner=user();editor=user();reader=user();other=user();keyword="isolation"+UUID.randomUUID().toString().replace("-","");
        category=category(owner,0L);members.addMember(category,owner,"OWNER",null);members.addMember(category,editor,"EDITOR",null);members.addMember(category,reader,"READER",null);
        document=doc(category);
    }
    Long user(){SysUser u=new SysUser();u.setUsername("doc-"+UUID.randomUUID());u.setPassword("unused");u.setRole("USER");users.insert(u);return u.getId();}
    Long category(Long ownerId,Long parent){DocCategory c=new DocCategory();c.setName(keyword);c.setOwnerId(ownerId);c.setParentId(parent);c.setStatus(1);c.setDocCount(1);categories.insert(c);return c.getId();}
    Long doc(Long cat){DocDocument d=new DocDocument();d.setCategoryId(cat);d.setTitle(keyword);d.setContent("PRIVATE "+keyword);d.setStatus(1);d.setViewCount(0);d.setReadCount(0);d.setFavoriteCount(0);docs.insert(d);return d.getId();}
    String auth(Long id){return "Bearer "+jwt.generateToken(id,"fixture","USER");}
    @Test void derivedKnowledgeCannotBypassSourcePermissions() throws Exception {
        KgEntity entity=new KgEntity();entity.setName(keyword);entity.setDescription("PRIVATE "+keyword);entity.setDocId(document);entity.setCategoryId(category);entity.setType("CONCEPT");entities.insert(entity);
        http.perform(get("/api/learning/mastery/"+entity.getId()).header("Authorization",auth(other))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        for(String action:List.of("generate","accept")) http.perform(post("/api/knowledge/content/"+action).header("Authorization",auth(other)).contentType("application/json").content("{\"knowledgeId\":"+entity.getId()+"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void publicKnowledgeCannotUseOtherUsersPrivateChapterContext() throws Exception {
        Long publicDoc=doc(null);KgEntity entity=new KgEntity();entity.setName(keyword);entity.setDescription(keyword);entity.setType("CONCEPT");entity.setDocId(publicDoc);entities.insert(entity);
        LearningPath path=new LearningPath();path.setTitle(keyword);path.setOwnerUserId(owner);path.setStatus(1);paths.insert(path);
        LearningChapter chapter=new LearningChapter();chapter.setPathId(path.getId());chapter.setTitle("PRIVATE "+keyword);chapter.setDocIds(document.toString());chapters.insert(chapter);
        for(String action:List.of("generate","accept")) http.perform(post("/api/knowledge/content/"+action).header("Authorization",auth(other)).contentType("application/json").content("{\"knowledgeId\":"+entity.getId()+",\"learningNodeId\":"+chapter.getId()+"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void anonymousCannotReadPrivateOrIncrementViews() throws Exception {http.perform(get("/api/docs/"+document)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));assertEquals(0,docs.selectById(document).getViewCount());}
    @Test void otherCannotReadPrivateOrIncrementViews() throws Exception {http.perform(get("/api/docs/"+document).header("Authorization",auth(other))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));assertEquals(0,docs.selectById(document).getViewCount());}
    @Test void readerCanReadButCannotEdit() throws Exception {http.perform(get("/api/docs/"+document).header("Authorization",auth(reader))).andExpect(status().isOk());http.perform(put("/api/docs/"+document).header("Authorization",auth(reader)).contentType("application/json").content("{\"content\":\"stolen\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));assertEquals("PRIVATE "+keyword,docs.selectById(document).getContent());}
    @Test void ownerAndEditorCanEditWithoutChangingCategoryCount() throws Exception {for(Long actor:List.of(owner,editor)){http.perform(put("/api/docs/"+document).header("Authorization",auth(actor)).contentType("application/json").content("{\"content\":\"updated\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));}assertEquals(1,categories.selectById(category).getDocCount());}
    @Test void moveRequiresTargetWritePermission() throws Exception {Long target=category(other,0L);http.perform(put("/api/docs/"+document).header("Authorization",auth(owner)).contentType("application/json").content("{\"categoryId\":"+target+"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));assertEquals(category,docs.selectById(document).getCategoryId());}
    @Test void createRequiresTargetPermission() throws Exception {http.perform(post("/api/docs").header("Authorization",auth(other)).contentType("application/json").content("{\"categoryId\":"+category+",\"title\":\"stolen\",\"content\":\"bad\",\"status\":1}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));assertEquals(1,sql.queryForObject("select count(*) from doc_document where category_id=? and deleted=0",Integer.class,category));}
    @Test void privateSearchHasNoRowsOrTotal() throws Exception {http.perform(get("/api/docs").param("keyword",keyword).header("Authorization",auth(other))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.records").isEmpty());}
    @Test void deniedFavoriteAndProgressHaveNoSideEffects() throws Exception {http.perform(post("/api/docs/"+document+"/favorite").header("Authorization",auth(other))).andExpect(status().isForbidden());http.perform(post("/api/docs/progress").header("Authorization",auth(other)).contentType("application/json").content("{\"docId\":"+document+",\"progress\":100}")).andExpect(status().isForbidden());assertEquals(0,sql.queryForObject("select count(*) from doc_favorite where doc_id=?",Integer.class,document));assertEquals(0,sql.queryForObject("select count(*) from doc_read_progress where doc_id=?",Integer.class,document));}
    @Test void revokedMembershipRemovesFavoritesAndRecent() throws Exception {http.perform(post("/api/docs/"+document+"/favorite").header("Authorization",auth(reader))).andExpect(status().isOk());http.perform(post("/api/docs/progress").header("Authorization",auth(reader)).contentType("application/json").content("{\"docId\":"+document+",\"progress\":10}")).andExpect(status().isOk());sql.update("update kb_member set deleted=1 where category_id=? and user_id=?",category,reader);for(String route:List.of("favorites","recent")){http.perform(get("/api/docs/"+route).header("Authorization",auth(reader))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());}}
    @Test void childInheritsReaderAndEditorPermissions() throws Exception {Long child=category(null,category);Long childDoc=doc(child);http.perform(get("/api/docs/"+childDoc).header("Authorization",auth(reader))).andExpect(status().isOk());http.perform(put("/api/docs/"+childDoc).header("Authorization",auth(editor)).contentType("application/json").content("{\"content\":\"child edit\"}")).andExpect(status().isOk());}
    @Test void editCannotInjectCountersOrSoftDeletion() throws Exception {http.perform(put("/api/docs/"+document).header("Authorization",auth(editor)).contentType("application/json").content("{\"viewCount\":999,\"favoriteCount\":999,\"deleted\":1,\"fileUrl\":\"/uploads/injected\",\"content\":\"valid\"}")).andExpect(status().isOk());DocDocument d=docs.selectById(document);assertNotNull(d);assertNotEquals(999,d.getViewCount());assertEquals(0,d.getFavoriteCount());assertNull(d.getFileUrl());}
    @Test void readerCannotRegenerateSharedSummary() throws Exception {http.perform(post("/api/docs/"+document+"/ai-summary").header("Authorization",auth(reader))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));assertNull(docs.selectById(document).getSummary());}
    @Test void graphAndCategoryTreeDoNotExposePrivateLabels() throws Exception {for(String route:List.of("/api/knowledge/graph","/api/categories/tree")){String body=http.perform(get(route).header("Authorization",auth(other))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(body.contains(keyword),route);}}
    @Test void extractionDeniedForReaderAndOther() throws Exception {for(Long actor:List.of(reader,other)){http.perform(post("/api/knowledge/extract/"+document).header("Authorization",auth(actor))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));}}
    @Test void originalFileCannotBypassDocumentAuthorization() throws Exception {String url="/uploads/doc-security-"+UUID.randomUUID()+".txt";sql.update("update doc_document set file_url=? where id=?",url,document);http.perform(get(url).header("Authorization",auth(other))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));http.perform(get(url)).andExpect(status().isUnauthorized());}
    @Test void privateDocumentCannotBeCopiedViaLearningGenerator() throws Exception {http.perform(post("/api/learning/my/flashcards/generate").header("Authorization",auth(other)).contentType("application/json").content("{\"docId\":"+document+"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));}
    @Test void flashcardCannotReferenceOtherUsersPath() throws Exception {LearningPath path=new LearningPath();path.setTitle(keyword);path.setOwnerUserId(other);path.setStatus(1);paths.insert(path);http.perform(post("/api/docs/"+document+"/ai-flashcards").param("pathId",path.getId().toString()).header("Authorization",auth(owner))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));}
    @Test void ragCannotInheritPastIndependentChildBoundary() {Long child=category(other,category);Long childDoc=doc(child);DocChunk chunk=new DocChunk();chunk.setDocId(childDoc);chunk.setChunkIndex(0);chunk.setContent(keyword);chunk.setCharCount(keyword.length());chunks.insert(chunk);assertTrue(retrieval.searchSimilar(keyword,5,reader).isEmpty());}
    @Test void importedQuizCannotLeakPrivateDocument() throws Exception {QuizQuestion q=new QuizQuestion();q.setTitle(keyword);q.setContent(keyword);q.setQuestionType("SHORT_ANSWER");q.setAnswer(keyword);q.setCategoryId(category);q.setDocId(document);q.setStatus(1);questions.insert(q);http.perform(get("/api/quiz/questions").param("categoryId",category.toString()).header("Authorization",auth(other))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());http.perform(post("/api/quiz/submit").header("Authorization",auth(other)).contentType("application/json").content("{\"answers\":[{\"questionId\":"+q.getId()+",\"userAnswer\":\"x\"}]}")).andExpect(status().isForbidden());}
    @Test void importedPrivatePathAndChapterCannotBeReadByOther() throws Exception {LearningPath p=new LearningPath();p.setTitle(keyword);p.setOwnerUserId(owner);p.setStatus(1);paths.insert(p);LearningChapter chapter=new LearningChapter();chapter.setPathId(p.getId());chapter.setTitle(keyword);chapter.setContent(keyword);chapter.setDocIds(document.toString());chapters.insert(chapter);http.perform(get("/api/learning/paths/"+p.getId()).header("Authorization",auth(other))).andExpect(status().isForbidden());http.perform(get("/api/learning/chapters/"+chapter.getId()).header("Authorization",auth(other))).andExpect(status().isForbidden());}
    @Test void processingCannotReadOrAcceptSomeoneElsesDocument() throws Exception {for(String route:List.of("/api/knowledge/process","/api/knowledge/process/accept")){http.perform(post(route).header("Authorization",auth(other)).contentType("application/json").content("{\"docId\":"+document+"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));}}
    @Test void codeMistakeRelatedDocsCannotLeakPrivateSnippets() throws Exception {String body=http.perform(post("/api/mistakes/collect-code").header("Authorization",auth(other)).contentType("application/json").content("{\"language\":\"python\",\"error\":\""+keyword+"\",\"code\":\"fixture\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(body.contains("PRIVATE "+keyword));}
}
