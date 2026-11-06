package com.knowflow.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2 模块边界严格门禁：扫描所有生产类型，存量违规也失败，不设置模块豁免。 */
class ModularLayeredArchitectureTest {
    private static final Set<String> MODULES = Set.of(
            "identity", "learning", "interview", "exam", "coding", "flashcards", "mastery",
            "knowledge", "ai", "agent", "voice", "reward", "store", "catalog", "techenglish",
            "community", "productivity", "workbench", "administration", "apps");
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.knowflow");

    private static String module(JavaClass type) {
        String[] parts = type.getPackageName().split("\\.");
        return parts.length > 2 && parts[0].equals("com") && parts[1].equals("knowflow")
                && MODULES.contains(parts[2]) ? parts[2] : "";
    }

    private static boolean layer(JavaClass type, String layer) {
        String owner = module(type);
        return !owner.isEmpty() && (type.getPackageName().equals("com.knowflow." + owner + "." + layer)
                || type.getPackageName().startsWith("com.knowflow." + owner + "." + layer + "."));
    }

    private static boolean foreign(JavaClass source, JavaClass target) {
        return !module(source).isEmpty() && !module(target).isEmpty()
                && !module(source).equals(module(target));
    }

    private static boolean mapper(JavaClass type) {
        return layer(type, "mapper") || type.isAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                || type.isAssignableTo("com.baomidou.mybatisplus.core.mapper.BaseMapper");
    }

    private static boolean entity(JavaClass type) {
        return layer(type, "entity") || type.isAnnotatedWith("com.baomidou.mybatisplus.annotation.TableName");
    }

    private static String role(JavaClass type) {
        String name = type.getSimpleName();
        if (type.isAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                || type.isAnnotatedWith("org.springframework.stereotype.Controller")
                || type.isMetaAnnotatedWith("org.springframework.stereotype.Controller")
                || name.endsWith("Controller")) return "controller";
        if (mapper(type)) return "mapper";
        if (entity(type)) return "entity";
        if (name.endsWith("ServiceImpl")) return "service.impl";
        if (type.isAnnotatedWith("org.springframework.stereotype.Service")
                || type.isMetaAnnotatedWith("org.springframework.stereotype.Service")
                || name.endsWith("Service")) return "service";
        if (name.endsWith("DTO")) return "dto";
        if (name.endsWith("VO")) return "vo";
        return "";
    }

    private static void reject(String rule, BiPredicate<JavaClass, JavaClass> forbidden) {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : CLASSES) {
            source.getDirectDependenciesFromSelf().forEach(edge -> {
                if (forbidden.test(source, edge.getTargetClass())) violations.add(edge.getDescription());
            });
        }
        assertTrue(violations.isEmpty(), () -> rule + " 违规数=" + violations.size() + "\n" + String.join("\n", violations));
    }

    @Test
    void arch004ServiceMustNotDependOnController() {
        reject("ARCH-004", (source, target) -> (layer(source, "service") || role(source).startsWith("service"))
                && role(target).equals("controller"));
    }

    @Test
    void arch005MapperOwnership() {
        reject("ARCH-005", (source, target) -> foreign(source, target)
                && (mapper(target) || (mapper(source) && entity(target))));
    }

    @Test
    void arch006EntityMustNotCrossModules() {
        reject("ARCH-006", (source, target) -> foreign(source, target) && entity(target));
    }

    @Test
    void arch007ImplementationMustNotCrossModules() {
        reject("ARCH-007", (source, target) -> foreign(source, target)
                && (layer(target, "service.impl") || target.getSimpleName().endsWith("ServiceImpl")));
    }

    @Test
    void arch008OnlyContractsMayCrossModules() {
        reject("ARCH-008", (source, target) -> (foreign(source, target) && !layer(target, "contract"))
                || (layer(source, "contract") && (entity(target) || mapper(target)
                || layer(target, "service") || layer(target, "dto") || layer(target, "vo")
                || target.getName().startsWith("com.baomidou.mybatisplus.core.conditions."))));
    }

    @Test
    void arch009CommonMustNotDependOnBusiness() {
        reject("ARCH-009", (source, target) -> (source.getPackageName().equals("com.knowflow.common")
                || source.getPackageName().startsWith("com.knowflow.common.")) && !module(target).isEmpty());
    }

    @Test
    void arch010BusinessModulesMustBeAcyclic() {
        Map<String, Set<String>> graph = new HashMap<>();
        for (String owner : MODULES) graph.put(owner, new HashSet<>());
        for (JavaClass source : CLASSES) source.getDirectDependenciesFromSelf().forEach(edge -> {
            if (foreign(source, edge.getTargetClass())) graph.get(module(source)).add(module(edge.getTargetClass()));
        });
        List<String> cycles = new ArrayList<>();
        for (String owner : MODULES) {
            if (reaches(owner, owner, graph, new HashSet<>())) cycles.add(owner);
        }
        assertTrue(cycles.isEmpty(), "ARCH-010 处于依赖环中的模块=" + cycles + "；含 Contract 依赖，非运行时调用证明");
    }

    private static boolean reaches(String current, String goal, Map<String, Set<String>> graph, Set<String> visited) {
        if (!visited.add(current)) return false;
        for (String next : graph.get(current)) {
            if (next.equals(goal) || reaches(next, goal, graph, visited)) return true;
        }
        return false;
    }

    @Test
    void arch011RolesMustUseTraditionalLayers() {
        List<String> violations = new ArrayList<>();
        for (JavaClass type : CLASSES) {
            if (type.getName().contains("$") || type.getSimpleName().equals("package-info")) continue;
            String owner = module(type), role = role(type);
            if (owner.isEmpty()) {
                String[] parts = type.getPackageName().split("\\.");
                if (!role.isEmpty() && parts.length > 2 && type.getPackageName().startsWith("com.knowflow.")
                        && !Set.of("common", "config", "exception", "framework", "security", "util", "utils",
                        "websocket", "seed").contains(parts[2])) violations.add(type.getName());
                if (type.getPackageName().matches("com\\.knowflow\\.(controller|service|mapper|entity|dto|vo)(\\..*)?")) {
                    violations.add(type.getName());
                }
                continue;
            }
            if (layer(type, "api") || layer(type, "web") || layer(type, "application")
                    || layer(type, "domain") || layer(type, "infrastructure")) violations.add(type.getName());
            if (!role.isEmpty() && !layer(type, role) && !(layer(type, "contract")
                    && Set.of("dto", "vo", "service").contains(role))) violations.add(type.getName());
        }
        assertTrue(violations.isEmpty(), "ARCH-011 " + violations);
    }

    @Test
    void arch012ModuleRootMustNotContainRoleClasses() {
        List<String> violations = new ArrayList<>();
        for (JavaClass type : CLASSES) {
            if (!type.getName().contains("$") && !module(type).isEmpty()
                    && type.getPackageName().equals("com.knowflow." + module(type)) && !role(type).isEmpty()) {
                violations.add(type.getName());
            }
        }
        assertTrue(violations.isEmpty(), "ARCH-012 " + violations);
    }
}
