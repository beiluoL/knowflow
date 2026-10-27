package com.knowflow.controller;

import com.knowflow.entity.DocCategory;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.service.EmbeddingService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.*;

/** Real Tomcat ASYNC completion; MockMvc initial dispatch alone cannot expose the committed-response failure. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"server.address=127.0.0.1","knowflow.datasource.h2.url=jdbc:h2:mem:oct2_sse_http;DB_CLOSE_DELAY=-1;MODE=MySQL"})
class ImportSseHttpCompletionTest {
    @Autowired TestRestTemplate http; @Autowired JwtUtils jwt; @Autowired DocCategoryMapper categories;
    @MockBean EmbeddingService embedding;
    @org.springframework.boot.test.mock.mockito.SpyBean com.knowflow.service.KnowledgeService knowledge;
    @Test void authenticatedMultipartStreamEndsWithCompleteEvent() {
        java.util.concurrent.atomic.AtomicReference<Long> extractionActor=new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(call->{extractionActor.set(com.knowflow.common.SecurityUtils.getCurrentUserIdNullable());return call.callRealMethod();}).when(knowledge).extractDoc(org.mockito.ArgumentMatchers.anyLong());
        var transport=new SimpleClientHttpRequestFactory();transport.setReadTimeout(10000);http.getRestTemplate().setRequestFactory(transport);
        DocCategory cat=new DocCategory();cat.setName("SSE HTTP fixture");cat.setOwnerId(2L);cat.setStatus(1);cat.setParentId(0L);categories.insert(cat);
        var parts=new LinkedMultiValueMap<String,Object>();parts.add("targetCategoryId",cat.getId().toString());parts.add("createSubCategories","false");
        parts.add("files",new ByteArrayResource("# Safe SSE\n\nOffline fixture.".getBytes(java.nio.charset.StandardCharsets.UTF_8)){@Override public String getFilename(){return "fixture.md";}});
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.MULTIPART_FORM_DATA);headers.setBearerAuth(jwt.generateToken(2L,"user1","USER"));
        var response=http.postForEntity("/api/knowledge/import/stream",new HttpEntity<>(parts,headers),String.class);
        assertEquals(HttpStatus.OK,response.getStatusCode());assertNotNull(response.getBody());assertTrue(response.getBody().contains("event:complete"),response.getBody());assertTrue(response.getBody().contains("\"successCount\":1"),response.getBody());
        assertEquals(2L,extractionActor.get(),"Async extraction must keep the authenticated actor");
    }
}
