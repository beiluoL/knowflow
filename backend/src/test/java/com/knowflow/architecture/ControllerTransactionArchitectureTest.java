package com.knowflow.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** HTTP 适配器必须将事务管理交给代理应用服务。 */
class ControllerTransactionArchitectureTest {
    @Test
    void controllersDoNotDeclareTransactions() {
        for (JavaClass type : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.knowflow")) {
            if (!type.isAnnotatedWith(RestController.class)
                    && !type.isAnnotatedWith(Controller.class)
                    && !type.isMetaAnnotatedWith(Controller.class)) continue;
            assertFalse(type.isAnnotatedWith(Transactional.class), type.getName());
            type.getMethods().forEach(method -> {
                assertFalse(method.isAnnotatedWith(Transactional.class), method.getFullName());
            });
        }
    }
}
