package com.knowflow.support;

import com.knowflow.KnowFlowApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

/** Disposable loopback QA only. Never registered in the production application. */
public final class ChapterRichMarkdownLiveServer {
    public static void main(String[] args) {
        var app = new SpringApplication(KnowFlowApplication.class, FakeAiConfiguration.class,
                InterviewSeedLiveServer.OfflineConfiguration.class);
        var context = app.run("--spring.profiles.active=local", "--server.address=127.0.0.1",
                "--server.port=8081", "--knowflow.host-capabilities.enabled=false",
                "--knowflow.seed.development-account.enabled=true", "--knowflow.datasource.h2.init-mode=auto",
                "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.slf4j.Slf4jImpl");
        var jdbc = context.getBean(JdbcTemplate.class);
        Long chapter = jdbc.queryForObject("SELECT c.id FROM learning_chapter c JOIN learning_path p ON p.id=c.path_id WHERE p.owner_user_id=0 AND c.title=? AND c.deleted=0", Long.class, "泛型、集合边界与 ConcurrentHashMap");
        String key = jdbc.queryForObject("SELECT resource_key FROM interview_seed_resource WHERE resource_type='CHAPTER' AND resource_id=?", String.class, chapter);
        String prefix = key.substring(0, key.length()-"chapter".length());
        Long single = jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE resource_key=?", Long.class, prefix+"question/0");
        Long multi = jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE resource_key=?", Long.class, prefix+"question/1");
        jdbc.update("UPDATE quiz_question SET title=?,question_type=?,options=?,answer=?,explanation=? WHERE id=?", "QA 单选：泛型检查发生在哪里？", "SINGLE_CHOICE", "[\"主要在编译期\",\"每个参数类型都是独立运行时类\"]", "0", "Java 泛型主要提供编译期类型约束。", single);
        jdbc.update("UPDATE quiz_question SET title=?,question_type=?,options=?,answer=?,explanation=? WHERE id=?", "QA 多选：ConcurrentHashMap 的边界", "MULTIPLE_CHOICE", "[\"不允许 null 键值\",\"容器安全不等于值对象安全\",\"任意跨键组合都是原子的\"]", "0,1", "单次映射操作安全不等于跨键业务事务。", multi);
        String content = """
                # 泛型、集合边界与 ConcurrentHashMap
                隔离 QA 阅读样例；不是用户的正式学习内容。
                ## 泛型与类型擦除
                中文、List<T>、Map<K, V>、≤ ≥ √ × ∞ ✓ ✗ ⚠️ 🚀 均作为正文显示。
                :::tip 主动回忆
                先尝试解释，再查看参考答案；展开内容不生成学习成绩。
                :::
                ### Java 示例
                ```java
                List<String> names = new ArrayList<>();
                String literal = "<script>alert(1)</script> & <T>";
                System.out.println(names.getClass());
                ```
                :::details 为什么不是两种运行时 List？
                类型参数主要用于编译期约束，不意味着每个参数化 List 都有独立运行时类。
                :::
                ## PECS 原则
                | 方向 | 通配符 |
                | --- | --- |
                | 生产者 | `? extends T` |
                | 消费者 | `? super T` |
                - [ ] 闭卷说明生产者与消费者
                  - 给出一个 API 例子
                :::warning 并发边界
                线程安全容器不自动保护可变 value；不要把任意组合操作视为事务。
                :::
                ## 本章真实练习
                :::quiz id="%s"
                :::
                :::quiz id="%s"
                :::
                :::quiz id="not-this-chapter"
                :::
                ## 本章总结
                静态自测清单不是完成状态，题目提交使用原有判分接口。
                """.formatted(single, multi);
        jdbc.update("UPDATE learning_chapter SET content=? WHERE id=?", content, chapter);
        System.out.println("V4_QA_CHAPTER="+chapter);
    }
}
