package com.knowflow.support;

import com.knowflow.KnowFlowApplication;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.nio.file.*;
import java.util.*;

/** Explicit manual loopback browser/HTTP fixture server, never part of production builds. */
public final class DocumentLiveServer {
    @TestConfiguration public static class OfflineConfiguration {
        @Bean @Primary EmbeddingService offlineEmbedding(){return org.mockito.Mockito.mock(EmbeddingService.class);}
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("knowflow-document-live-");Path source=Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("safe.md"),"# Safe fixture\n\n## Definition\nCapture and review offline.\n\n## Usage\nReview then apply.");
        String[] options={"--server.address=127.0.0.1","--server.port=8080","--app.upload.dir="+root.resolve("uploads"),"--knowflow.datasource.h2.url=jdbc:h2:mem:document_live;DB_CLOSE_DELAY=-1;MODE=MySQL","--knowflow.host-capabilities.enabled=true","--knowflow.host-capabilities.authorized-user-ids=2","--knowflow.host-capabilities.allowed-roots="+source,"--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.slf4j.Slf4jImpl"};
        var context=SpringApplication.run(new Class<?>[]{KnowFlowApplication.class,FakeAiConfiguration.class,OfflineConfiguration.class},options);
        var users=context.getBean(SysUserMapper.class);var categories=context.getBean(DocCategoryMapper.class);var members=context.getBean(KbMemberService.class);var docs=context.getBean(DocDocumentMapper.class);
        Long editor=user(users,"verify-editor");Long other=user(users,"verify-other");
        DocCategory cat=new DocCategory();cat.setName("验证 · 私有知识库");cat.setOwnerId(2L);cat.setStatus(1);cat.setParentId(0L);cat.setDocCount(1);categories.insert(cat);
        members.addMember(cat.getId(),2L,"OWNER",null);members.addMember(cat.getId(),3L,"READER",null);members.addMember(cat.getId(),editor,"EDITOR",null);
        Path assets=Files.createDirectories(root.resolve("uploads/verify"));Files.writeString(assets.resolve("safe.md"),"SAFE ORIGINAL");Files.write(assets.resolve("safe.png"),Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aBfkAAAAASUVORK5CYII="));
        DocDocument doc=new DocDocument();doc.setTitle("权限验证文档");doc.setCategoryId(cat.getId());doc.setContent("# 权限验证\n\nPRIVATE FIXTURE\n\n![授权图片](/uploads/verify/safe.png)");doc.setFileName("safe.md");doc.setFileUrl("/uploads/verify/safe.md");doc.setStatus(1);docs.insert(doc);
        Files.copy(assets.resolve("safe.png"),assets.resolve("public.png"));
        DocDocument publicDoc=new DocDocument();publicDoc.setTitle("公开附件验证");publicDoc.setContent("![public](/uploads/verify/public.png)");publicDoc.setStatus(1);docs.insert(publicDoc);
        Map<String,Object> fixture=Map.of("category",cat.getId(),"doc",doc.getId(),"editor",editor,"other",other,"source",source.toString(),"root",root.toString());
        new ObjectMapper().writeValue(root.resolve("fixture.json").toFile(),fixture);
        System.out.println("DOCUMENT_LIVE_FIXTURE="+root.resolve("fixture.json"));
    }
    private static Long user(SysUserMapper users,String username){SysUser user=new SysUser();user.setUsername(username);user.setPassword(users.selectById(2L).getPassword());user.setRole("USER");users.insert(user);return user.getId();}
}
