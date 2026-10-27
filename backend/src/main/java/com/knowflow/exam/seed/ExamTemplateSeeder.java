package com.knowflow.exam.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认试卷模板种子（幂等）：按 template_key upsert 10 套 Java + AI 模板。
 *
 * <p>模板只是「组卷规格」，不是作答记录；实际题目快照在组卷时才生成。
 * 题目来自隔离题库（本地 H2 为 SEED_EXAM 种子；正式库需先导入通过审核的题库）。
 */
@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class ExamTemplateSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private record Tpl(String key, String name, String desc, String domain, List<String> topics,
                       Map<String, Integer> dist, int minutes, String mode) {
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Tpl> tpls = List.of(
                new Tpl("java-collections", "Java 集合专项", "HashMap / ConcurrentHashMap 原理、树化、并发与 sizeCtl。",
                        "JAVA", List.of("HASHMAP", "CONCURRENT_HASHMAP"),
                        dist(s(4), m(2), tf(1), fb(1), sa(2)), 25, "PRACTICE"),
                new Tpl("java-concurrency", "Java 并发与线程池专项", "AQS、ReentrantLock、线程池调度、拒绝策略与线程饥饿。",
                        "JAVA", List.of("CONCURRENT_HASHMAP", "AQS", "THREADPOOL"),
                        dist(s(4), m(3), tf(2), fb(2), sa(1)), 30, "EXAM"),
                new Tpl("jvm-gc", "JVM 与 GC 专项", "内存布局、G1、GC Roots、对象分配与线上排查。",
                        "JAVA", List.of("JVM"),
                        dist(s(2), m(1), tf(1), fb(1), sa(1)), 15, "PRACTICE"),
                new Tpl("spring-tx", "Spring 与事务专项", "声明式事务、传播行为、失效场景与代理机制。",
                        "JAVA", List.of("SPRING"),
                        dist(s(2), m(1), tf(1), fb(1), sa(1)), 15, "PRACTICE"),
                new Tpl("mysql-redis", "MySQL 与 Redis 专项", "MVCC、隔离级别、索引、缓存三大问题与一致性。",
                        "JAVA", List.of("MYSQL", "REDIS"),
                        dist(s(3), m(2), tf(2), fb(2)), 20, "PRACTICE"),
                new Tpl("java-advanced-mock", "Java 高级综合模拟", "集合/并发/JVM/Spring/MySQL 综合限时模拟考。",
                        "JAVA", List.of("HASHMAP", "CONCURRENT_HASHMAP", "AQS", "THREADPOOL", "JVM", "SPRING", "MYSQL"),
                        dist(s(6), m(3), tf(2), fb(2), sa(1)), 35, "EXAM"),
                new Tpl("rag-embedding", "RAG 与 Embedding 专项", "切片、向量检索、评测、引用与幻觉缓解。",
                        "AI", List.of("RAG"),
                        dist(s(2), m(1), tf(1), fb(1), sa(1)), 15, "PRACTICE"),
                new Tpl("tool-calling-agent", "Tool Calling 与 Agent 专项", "函数调用、ReAct、工具鉴权与 Agent 安全设计。",
                        "AI", List.of("AGENT"),
                        dist(s(2), m(1), tf(1), fb(1), sa(1)), 15, "PRACTICE"),
                new Tpl("java-ai-mock", "Java + AI 综合模拟", "Java 基础与 RAG/Agent 应用开发综合模拟考。",
                        "JAVA_AI", List.of("HASHMAP", "CONCURRENT_HASHMAP", "AQS", "THREADPOOL", "JVM",
                        "SPRING", "MYSQL", "REDIS", "RAG", "AGENT"),
                        dist(s(6), m(3), tf(2), fb(2), sa(2)), 40, "EXAM"),
                new Tpl("java-ai-architecture", "Java + AI 项目架构面试", "开放式项目架构与线上运营，以主观题为主，AI 辅助评卷。",
                        "JAVA_AI", List.of("PROJECT", "SPRING", "RAG", "AGENT"),
                        dist(sa(5)), 30, "EXAM")
        );
        for (Tpl t : tpls) {
            try {
                upsert(t);
            } catch (Exception e) {
                log.warn("考试模板种子写入失败 {}: {}", t.key(), e.getMessage());
            }
        }
    }

    private void upsert(Tpl t) throws Exception {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam_template WHERE template_key = ? AND deleted = 0",
                Integer.class, t.key());
        if (exists != null && exists > 0) {
            return;
        }
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("topics", t.topics());
        spec.put("typeDistribution", t.dist());
        spec.put("strategy", "BALANCED");
        spec.put("multiPolicy", "PARTIAL_CREDIT");
        jdbc.update("INSERT INTO exam_template (template_key, name, description, domain, topics, spec, "
                        + "duration_minutes, total_score, pass_score, mode, status, version, create_time, update_time, deleted) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                t.key(), t.name(), t.desc(), t.domain(), String.join(",", t.topics()),
                objectMapper.writeValueAsString(spec),
                t.minutes(), 100, 60, t.mode(), 1, "1.0");
    }

    private static final String SINGLE = "SINGLE_CHOICE";
    private static final String MULTI = "MULTIPLE_CHOICE";
    private static final String TF = "TRUE_FALSE";
    private static final String FB = "FILL_BLANK";
    private static final String SA = "SHORT_ANSWER";

    private static Map.Entry<String, Integer> s(int n) {
        return Map.entry(SINGLE, n);
    }

    private static Map.Entry<String, Integer> m(int n) {
        return Map.entry(MULTI, n);
    }

    private static Map.Entry<String, Integer> tf(int n) {
        return Map.entry(TF, n);
    }

    private static Map.Entry<String, Integer> fb(int n) {
        return Map.entry(FB, n);
    }

    private static Map.Entry<String, Integer> sa(int n) {
        return Map.entry(SA, n);
    }

    @SafeVarargs
    private static Map<String, Integer> dist(Map.Entry<String, Integer>... entries) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return map;
    }
}
