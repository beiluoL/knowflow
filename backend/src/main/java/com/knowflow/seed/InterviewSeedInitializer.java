package com.knowflow.seed;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** Only provisions version indices. Never creates content or user state at startup. */
@Component @org.springframework.core.annotation.Order(0) @RequiredArgsConstructor
public class InterviewSeedInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    @Override public void run(ApplicationArguments args) {
        var script=new ResourceDatabasePopulator(new ClassPathResource("seed/seed-index.sql"));
        script.execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        try {
            jdbc.update("INSERT INTO interview_seed_pack(seed_key,initialized) SELECT ?,0 WHERE NOT EXISTS (SELECT 1 FROM interview_seed_pack WHERE seed_key=?)",
                    InterviewSeedCatalog.KEY,InterviewSeedCatalog.KEY);
        } catch (DuplicateKeyException race) {
            // Another application instance created the same immutable lock row.
            if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_pack WHERE seed_key=?",Integer.class,InterviewSeedCatalog.KEY)!=1) throw race;
        }
        jdbc.update("INSERT INTO interview_seed_pack(seed_key,initialized) SELECT ?,0 WHERE NOT EXISTS (SELECT 1 FROM interview_seed_pack WHERE seed_key=?)",CollectionsDepthService.KEY,CollectionsDepthService.KEY);
        jdbc.update("INSERT INTO interview_seed_pack(seed_key,initialized) SELECT ?,0 WHERE NOT EXISTS (SELECT 1 FROM interview_seed_pack WHERE seed_key=?)",GenericsDepthService.KEY,GenericsDepthService.KEY);
        jdbc.update("INSERT INTO interview_seed_pack(seed_key,initialized) SELECT ?,0 WHERE NOT EXISTS (SELECT 1 FROM interview_seed_pack WHERE seed_key=?)",ContentRebuildService.KEY,ContentRebuildService.KEY);
    }
}
