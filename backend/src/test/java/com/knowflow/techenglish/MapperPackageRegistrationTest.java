package com.knowflow.techenglish;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.techenglish.entity.TechVocabConceptLink;
import com.knowflow.techenglish.mapper.TechVocabConceptLinkMapper;
import com.knowflow.techenglish.mapper.TechVocabPronunciationMapper;
import com.knowflow.techenglish.mapper.TechVocabTermMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** 验证仅移动包后真实 Mapper 注册及持久化行为。 */
@SpringBootTest
class MapperPackageRegistrationTest {
    @Autowired ApplicationContext context;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TechVocabConceptLinkMapper links;

    @Test void eachMapperHasOneBeanInItsOwnedPackage() {
        for (Class<?> type : new Class<?>[]{TechVocabTermMapper.class,
                TechVocabPronunciationMapper.class, TechVocabConceptLinkMapper.class}) {
            assertEquals("com.knowflow.techenglish.mapper", type.getPackageName());
            assertEquals(1, context.getBeansOfType(type).size());
        }
    }

    @Test void wrapperCrudAndEntityMappingParticipateInRollback() {
        var row = new TechVocabConceptLink();
        row.setTermId(Long.MAX_VALUE - 37);
        row.setTargetType("TERM");
        row.setTargetId(Long.MAX_VALUE - 38);
        row.setRelationType("RELATED");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertEquals(1, links.insert(row));
            assertNotNull(row.getId());
            assertEquals(row.getTargetId(), links.selectById(row.getId()).getTargetId());
            assertEquals(1L, links.selectCount(new LambdaQueryWrapper<TechVocabConceptLink>()
                    .eq(TechVocabConceptLink::getId, row.getId())));
            row.setRelationType("CONTRASTS");
            assertEquals(1, links.updateById(row));
            assertEquals("CONTRASTS", links.selectById(row.getId()).getRelationType());
            status.setRollbackOnly();
        });
        assertNull(links.selectById(row.getId()));
    }
}
