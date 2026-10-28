package com.knowflow.coding.workspace;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** 与现有学习模块一致，按开关执行可重复的增量DDL；生产仍需单独迁移验收。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
  name = "knowflow.product.java-practice.enabled",
  havingValue = "true"
)
public class JavaPracticeSchema {

  private final DataSource dataSource;

  @PostConstruct
  void initialize() {
    new ResourceDatabasePopulator(
      new ClassPathResource(
        "db/h2/migration/V20261028__java_practice_workspace.sql"
      )
    ).execute(dataSource);
  }
}
