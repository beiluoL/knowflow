package com.knowflow.seed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ContentStudioDatabaseGuardTest {
 private MockEnvironment env(){Path root=Path.of(System.getProperty("user.dir"));if(root.getFileName().toString().equals("backend"))root=root.getParent();var e=new MockEnvironment().withProperty("knowflow.content-studio.qa.package",root.resolve("content-studio/java-ai-interview-v2").toString()).withProperty("server.address","127.0.0.1").withProperty("knowflow.datasource.allow-runtime-switch","false");e.setActiveProfiles("local");return e;}
 @Test void refusesOrdinaryDatabaseEvenWithFlagAndLocalProfile()throws Exception{var d=new DriverManagerDataSource("jdbc:h2:mem:not_a_studio_database","sa","");var s=new ContentStudioHashMapService(new JdbcTemplate(d),new ObjectMapper(),env());var e=assertThrows(BusinessException.class,()->s.dryRun(1));assertEquals(403,e.getCode());}
 @Test void refusesProductionProfileBeforeAnyWrite()throws Exception{var e=env();e.setActiveProfiles("local","production");var d=new DriverManagerDataSource("jdbc:h2:mem:phase3a-qa-test-guard","sa","");var s=new ContentStudioHashMapService(new JdbcTemplate(d),new ObjectMapper(),e);assertEquals(403,assertThrows(BusinessException.class,()->s.dryRun(1)).getCode());}
 @Test void rejectsSymlinkedDatabaseAndDirectory()throws Exception{
  Path root=Files.createTempDirectory("studio-guard-").toRealPath();Path qa=Files.createDirectories(root.resolve("backend/data/phase3a-qa-check"));Path db=qa.resolve("knowledge.mv.db");Files.writeString(db,"not a database; pure path test");String url="jdbc:h2:file:"+qa.resolve("knowledge");assertTrue(ContentStudioHashMapService.isQaDatabaseFile(url,root));
  Path external=Files.createTempFile("outside-studio-",".db");Files.delete(db);Files.createSymbolicLink(db,external);assertFalse(ContentStudioHashMapService.isQaDatabaseFile(url,root));Files.delete(db);Files.delete(qa);Files.createSymbolicLink(qa,external.getParent());assertFalse(ContentStudioHashMapService.isQaDatabaseFile(url,root));Files.delete(qa);Files.delete(external);Files.delete(root.resolve("backend/data"));Files.delete(root.resolve("backend"));Files.delete(root);
 }

}
