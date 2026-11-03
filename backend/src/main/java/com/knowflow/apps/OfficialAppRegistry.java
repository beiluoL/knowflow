package com.knowflow.apps;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 官方 App 注册表（Architecture-3，§34/§124/§161-163）。
 *
 * 背景：AppDefinition 在前端静态 Registry（Built-in），后端需要「App 是否可用」来判断购买门禁
 * （APP_DISABLED，Architecture-2 遗留）与校验 user_app.app_key 白名单（§34/§124）。
 * 本类为**后端静态官方白名单**（不建 app_definition 表，§23/§160-162），与前端 registry.ts 的实际 App 注册保持同步。
 *
 * 前后端漂移风险（§165）：若前端 ACTIVE 而后端 DISABLED → 用户可看到但不可购买（购买由后端拦截）。
 */
@Component
public class OfficialAppRegistry {

    /** 官方 App 状态（MVP 三态：ACTIVE/DISABLED/LEGACY，§67；当前全部 ACTIVE，§69）。 */
    private static final Map<String, String> APP_STATUS = new LinkedHashMap<>();

    /** 新用户默认安装（与 Architecture-1 DEFAULT_APP_KEYS 一致，§39：不重新定义）。 */
    public static final List<String> DEFAULT_APP_KEYS =
            List.of("flashcard", "quiz", "mistake", "focus", "mock-interview");

    static {
        APP_STATUS.put("capture", "ACTIVE");
        APP_STATUS.put("spaced-review", "ACTIVE");
        APP_STATUS.put("flashcard", "ACTIVE");
        APP_STATUS.put("quiz", "ACTIVE");
        APP_STATUS.put("exam", "ACTIVE");
        APP_STATUS.put("mock-interview", "ACTIVE"); // Phase 3H 模拟面试（基础模式，无 AI 依赖）
        APP_STATUS.put("mistake", "ACTIVE");
        APP_STATUS.put("focus", "ACTIVE");
        APP_STATUS.put("feynman", "ACTIVE");
        APP_STATUS.put("cornell", "ACTIVE");
        APP_STATUS.put("memory-palace", "ACTIVE");
        APP_STATUS.put("knowledge-graph", "ACTIVE");
        APP_STATUS.put("coding", "ACTIVE");
        APP_STATUS.put("mindmap", "ACTIVE");
        APP_STATUS.put("drawing", "ACTIVE");
        APP_STATUS.put("ai-writing", "ACTIVE");
        APP_STATUS.put("notes", "ACTIVE");
        APP_STATUS.put("tech-english", "ACTIVE"); // Java + AI 专业英语（P0 词汇大厅，与前端 registry 保持同步）
        // UI/UX V1.0：效率工具应用化（复用既有任务/签到/习惯/日历/计划页面，与前端 registry 保持同步）
        APP_STATUS.put("tasks", "ACTIVE");
        APP_STATUS.put("check-in", "ACTIVE");
        APP_STATUS.put("habits", "ACTIVE");
        APP_STATUS.put("calendar", "ACTIVE");
        APP_STATUS.put("today-plan", "ACTIVE");
        // Career Workbench：个人求职工作台（与前端 registry.ts 保持同步；默认不安装）
        APP_STATUS.put("career-workbench", "ACTIVE");
        // Career Apps V2.0：求职能力拆分为独立应用，共用同一套简历/JD/匹配/面试数据。
        APP_STATUS.put("career-overview", "ACTIVE");
        APP_STATUS.put("career-discover", "ACTIVE");
        APP_STATUS.put("career-resume", "ACTIVE");
        APP_STATUS.put("career-jobs", "ACTIVE");
        APP_STATUS.put("career-matching", "ACTIVE");
        APP_STATUS.put("career-interviews", "ACTIVE");
        APP_STATUS.put("career-resume-toolbox", "ACTIVE");
        APP_STATUS.put("career-resume-templates", "ACTIVE");
        // Voice Lab：本地优先语音实验室（与前端 registry.ts 保持同步；默认不安装）
        APP_STATUS.put("voice-lab", "ACTIVE");
    }

    /**
     * Career V1.0 → V2.0 兼容迁移：原 career-workbench 用户的五个业务中心对应应用。
     *
     * <p>只补偿「从未存在过记录」的应用，不覆盖用户显式停用；顺序与产品菜单一致。</p>
     */
    public static final List<String> CAREER_COMPANION_KEYS = List.of(
            "career-overview", "career-resume", "career-jobs", "career-matching", "career-interviews");

    /**
     * 两个新增应用不参与自动补偿，必须由用户主动获取（避免代用户做决定）。
     */
    public static final List<String> CAREER_OPT_IN_KEYS = List.of(
            "career-resume-toolbox", "career-resume-templates");

    /** appKey 是否属于官方注册表（白名单，§34/§124） */
    public boolean isValid(String appKey) {
        return appKey != null && APP_STATUS.containsKey(appKey);
    }

    /** App 平台层面是否可用（购买门禁，§72/§151） */
    public boolean isActive(String appKey) {
        return appKey != null && "ACTIVE".equals(APP_STATUS.get(appKey));
    }

    /** App 当前状态（ACTIVE/DISABLED/LEGACY） */
    public String statusOf(String appKey) {
        return APP_STATUS.getOrDefault(appKey, "UNKNOWN");
    }
}
