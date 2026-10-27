package com.knowflow.coding.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.coding.entity.QuestionTopic;
import com.knowflow.coding.mapper.QuestionTopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3G 知识点分类播种（幂等）：13 个一级分类 + 「数据结构与算法」14 个二级分类。
 * 已存在的 code 不覆盖（允许人工改名），只补缺失行。
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class QuestionTopicSeeder implements ApplicationRunner {

    private final QuestionTopicMapper topicMapper;

    /** 一级分类 code -> 名称（顺序即展示顺序） */
    private static final LinkedHashMap<String, String> ROOTS = new LinkedHashMap<>();

    static {
        ROOTS.put("JAVA_BASIC", "Java 基础");
        ROOTS.put("JAVA_COLLECTION", "Java 集合");
        ROOTS.put("JAVA_CONCURRENT", "Java 并发");
        ROOTS.put("JVM", "JVM");
        ROOTS.put("SPRING", "Spring");
        ROOTS.put("MYSQL", "MySQL");
        ROOTS.put("REDIS", "Redis");
        ROOTS.put("MQ_DISTRIBUTED", "MQ / 分布式");
        ROOTS.put("ALGO", "数据结构与算法");
        ROOTS.put("AI_LLM", "AI / LLM");
        ROOTS.put("RAG", "RAG");
        ROOTS.put("AGENT_MCP", "Agent / MCP");
        ROOTS.put("JAVA_AI_PRACTICE", "Java + AI 项目实战");
    }

    /** ALGO 二级分类 */
    private static final LinkedHashMap<String, String> ALGO_CHILDREN = new LinkedHashMap<>();

    static {
        ALGO_CHILDREN.put("ALGO_ARRAY", "数组");
        ALGO_CHILDREN.put("ALGO_STRING", "字符串");
        ALGO_CHILDREN.put("ALGO_HASH", "哈希表");
        ALGO_CHILDREN.put("ALGO_TWO_POINTERS", "双指针");
        ALGO_CHILDREN.put("ALGO_SLIDING_WINDOW", "滑动窗口");
        ALGO_CHILDREN.put("ALGO_BINARY_SEARCH", "二分查找");
        ALGO_CHILDREN.put("ALGO_LINKED_LIST", "链表");
        ALGO_CHILDREN.put("ALGO_STACK_QUEUE", "栈与队列");
        ALGO_CHILDREN.put("ALGO_TREE", "二叉树");
        ALGO_CHILDREN.put("ALGO_HEAP", "堆");
        ALGO_CHILDREN.put("ALGO_GRAPH", "图");
        ALGO_CHILDREN.put("ALGO_BACKTRACKING", "回溯");
        ALGO_CHILDREN.put("ALGO_GREEDY", "贪心");
        ALGO_CHILDREN.put("ALGO_DP", "动态规划");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        int inserted = 0;
        int order = 0;
        for (Map.Entry<String, String> e : ROOTS.entrySet()) {
            if (ensure(e.getKey(), e.getValue(), null, 1, order)) {
                inserted++;
            }
            order++;
        }
        order = 0;
        for (Map.Entry<String, String> e : ALGO_CHILDREN.entrySet()) {
            if (ensure(e.getKey(), e.getValue(), "ALGO", 2, order)) {
                inserted++;
            }
            order++;
        }
        if (inserted > 0) {
            log.info("[QuestionTopicSeeder] 新增知识点分类 {} 个（幂等跳过已存在项）", inserted);
        }
    }

    private boolean ensure(String code, String name, String parent, int level, int sortOrder) {
        QuestionTopic existing = topicMapper.selectOne(new LambdaQueryWrapper<QuestionTopic>()
                .eq(QuestionTopic::getCode, code).last("LIMIT 1"));
        if (existing != null) {
            // 仅补状态/排序，不覆盖人工改名
            boolean dirty = false;
            if (existing.getStatus() == null) {
                existing.setStatus(1);
                dirty = true;
            }
            if (existing.getSortOrder() == null) {
                existing.setSortOrder(sortOrder);
                dirty = true;
            }
            if (dirty) {
                topicMapper.updateById(existing);
            }
            return false;
        }
        QuestionTopic t = new QuestionTopic();
        t.setCode(code);
        t.setName(name);
        t.setParentCode(parent);
        t.setLevel(level);
        t.setSortOrder(sortOrder);
        t.setStatus(1);
        topicMapper.insert(t);
        return true;
    }

    /** 供其他 Seeder 使用的分类编码。 */
    public static List<String> algoChildCodes() {
        return List.copyOf(ALGO_CHILDREN.keySet());
    }
}
