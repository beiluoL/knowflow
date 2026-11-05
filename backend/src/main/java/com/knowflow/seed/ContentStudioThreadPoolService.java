package com.knowflow.seed;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
/** 仅供 QA 使用的固定适配器，复用 HashMap 引擎的全部防护和台账规则。 */
@Service @ConditionalOnProperty(name="knowflow.content-studio.qa.enabled",havingValue="true")
public class ContentStudioThreadPoolService {
 private final ContentStudioHashMapService engine;
 public ContentStudioThreadPoolService(JdbcTemplate jdbc,ObjectMapper json,Environment env)throws Exception {engine=new ContentStudioHashMapService(jdbc,json,env,"threadpool");}
 @Transactional(readOnly=true) public Map<String,Object> dryRun(long actor){return engine.dryRun(actor);}
 @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> apply(long actor,String hash){return engine.apply(actor,hash);}
 @Transactional(rollbackFor=Exception.class) public Map<String,Object> rollback(long actor){return engine.rollback(actor);}
 @Transactional(readOnly=true) public Map<String,Object> revision(long actor,long chapter){return engine.revision(actor,chapter);}
}
