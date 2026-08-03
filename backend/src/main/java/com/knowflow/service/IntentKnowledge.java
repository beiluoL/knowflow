package com.knowflow.service;

import java.util.List;
import java.util.Map;

/**
 * 领域知识库：用于辅助意图消歧与歧义检测的轻量结构化知识。
 * <p>
 * 覆盖 P4 三类知识：编程语言特性、框架约定、常见开发场景。
 * 当前以内置规则表承载（贴合项目「大功能才动架构」约定，未引入向量库）；
 * 后续可平滑升级为 embeddings 检索——结构不变，仅把 {@link #asPrompt()} 的输入替换为检索结果。
 */
public final class IntentKnowledge {

    /** 编程语言特性：易产生歧义或需澄清的点 */
    public static final Map<String, String> LANG_SPECS = Map.of(
            "javascript", "JS 无 interface/类型；回调与 Promise/async 易混；建议明确是否用 TS",
            "typescript", "TS 需声明类型；组件 props/state 应给类型；注意 strict 模式",
            "python", "Python 缩进敏感；2/3 差异；建议明确版本与是否用类型注解",
            "java", "Java 需类与 main 入口；Spring 场景需 @SpringBootApplication",
            "html", "HTML 不建议内联大量 JS；交互逻辑应放独立 .js 并以 <script src> 引入",
            "css", "CSS 优先放独立样式文件或用 scoped；避免全局污染"
    );

    /** 框架约定：生成代码前用户常需确认的目标 */
    public static final Map<String, String> FRAMEWORK_CONVENTIONS = Map.of(
            "vue", "Vue3 单文件组件(.vue)，组合式 API <script setup>；组件放 components/，路由用 vue-router",
            "react", "React 函数组件 + Hooks；状态用 useState/useReducer；组件放 src/components",
            "vite", "Vite 项目入口 index.html + src/main.js；依赖 npm install",
            "spring-boot", "Spring Boot 分层 controller/service/mapper；启动类含 @SpringBootApplication"
    );

    /** 常见开发场景：默认产物结构建议（与 CodeGenService 分文件约定对齐） */
    public static final Map<String, String> SCENARIO_TEMPLATES = Map.of(
            "pomodoro", "番茄钟：index.html(结构+内联CSS) + app.js(25分钟专注/5分钟休息/循环/开始-暂停-重置/音效)",
            "timer", "计时器：index.html + app.js(开始/暂停/重置 + 显示)",
            "todo", "待办：index.html + app.js(增删改 + 本地存储)",
            "calculator", "计算器：index.html + app.js(表达式求值)",
            "game", "小游戏：index.html + app.js(主循环 + 状态)"
    );

    /** 生成 Markdown 文本，供注入 system prompt 消歧 */
    public static String asPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("【语言特性】\n");
        LANG_SPECS.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        sb.append("【框架约定】\n");
        FRAMEWORK_CONVENTIONS.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        sb.append("【常见场景默认结构】\n");
        SCENARIO_TEMPLATES.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }

    /** 常见场景关键词，用于前端/后端快速提示澄清选项 */
    public static List<String> scenarioKeywords() {
        return List.copyOf(SCENARIO_TEMPLATES.keySet());
    }

    private IntentKnowledge() {
    }
}
