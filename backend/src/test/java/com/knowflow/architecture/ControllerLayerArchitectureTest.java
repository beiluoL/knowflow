package com.knowflow.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;

/** 检查全部生产 MVC 类型，不依赖文件名或包位置。 */
class ControllerLayerArchitectureTest {
    @Test
    void controllersMustNotDependOnPersistenceOrServiceLocators() {
        List<String> violations = new ArrayList<>();
        for (JavaClass type : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.knowflow")) {
            if (!type.isAnnotatedWith(RestController.class)
                    && !type.isAnnotatedWith(Controller.class)
                    && !type.isMetaAnnotatedWith(Controller.class)) continue;
            type.getDirectDependenciesFromSelf().forEach(dependency -> {
                JavaClass target = dependency.getTargetClass();
                String name = target.getName();
                if (name.contains(".mapper.")
                        || target.isAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                        || target.isAssignableTo("com.baomidou.mybatisplus.core.mapper.BaseMapper")
                        || name.startsWith("org.springframework.jdbc.")
                        || name.startsWith("java.sql.") || name.equals("javax.sql.DataSource")
                        || name.startsWith("org.apache.ibatis.session.")
                        || name.equals("org.springframework.context.ApplicationContext")
                        || name.equals("org.springframework.beans.factory.BeanFactory")) {
                    violations.add(dependency.getDescription());
                }
            });
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
