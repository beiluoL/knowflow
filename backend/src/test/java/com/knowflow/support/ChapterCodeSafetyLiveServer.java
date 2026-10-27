package com.knowflow.support;

import com.knowflow.KnowFlowApplication;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningPath;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningPathMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

/** Loopback-only, disposable QA content; no fixture is registered in the production application. */
public final class ChapterCodeSafetyLiveServer {
    public static void main(String[] args) {
        var app = new SpringApplication(KnowFlowApplication.class, FakeAiConfiguration.class,
                InterviewSeedLiveServer.OfflineConfiguration.class);
        var context = app.run("--spring.profiles.active=local", "--server.address=127.0.0.1",
                "--server.port=8081", "--knowflow.host-capabilities.enabled=false",
                "--knowflow.seed.development-account.enabled=true", "--knowflow.datasource.h2.init-mode=auto",
                "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.slf4j.Slf4jImpl");
        var jdbc = context.getBean(JdbcTemplate.class);
        Long actor = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=? AND deleted=0", Long.class, "seed-personal-v1");
        var path = new LearningPath();
        path.setTitle("QA · 章节源码安全"); path.setDescription("隔离验证内容，不是正式学习资料");
        path.setOwnerUserId(actor); path.setStatus(1); path.setChapterCount(1); path.setSortOrder(0);
        path.setLevel("入门"); path.setTotalDuration(5); path.setEnrolledCount(0);
        context.getBean(LearningPathMapper.class).insert(path);
        var chapter = new LearningChapter();
        chapter.setPathId(path.getId()); chapter.setSortOrder(1); chapter.setDuration(5);
        chapter.setTitle("源码必须作为文本显示");
        chapter.setContent("## 只读代码示例\n\n```js-run\nconst literal = '</textarea><span data-unsafe-code=\"yes\">literal</span>';\nconsole.log(literal);\n```\n\n## 声明式回忆\n\n```quiz-run\nQ: 源码可以访问登录页面会话吗？\nA. 不应该\nB. 可以\n答案: A\n解析: 阅读源码不授予执行权限。\n```");
        context.getBean(LearningChapterMapper.class).insert(chapter);
        System.out.println("QA_CHAPTER_ROUTE=/learning/chapter/" + chapter.getId());
    }
}
