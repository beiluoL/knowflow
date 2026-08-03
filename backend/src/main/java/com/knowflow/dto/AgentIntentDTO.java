package com.knowflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 意图识别请求：携带多轮上下文与（可选的）项目结构快照，由 {@link com.knowflow.service.IntentService}
 * 调用大模型做「带上下文的意图分类 + 歧义检测」。
 */
@Data
public class AgentIntentDTO {
    /** 当前用户输入 */
    private String currentInput;
    /** 近 K 轮历史，每条含 role/content/intent/slots，用于指代消解与意图动态修正 */
    private List<HistoryItem> history;
    /** 当前挂载项目的目录结构快照（相对路径 + 类型），用于结构歧义探针 */
    private List<ProjectFile> projectSnapshot;
    /** 是否仅做结构探针（不调用 LLM 的轻量模式） */
    private Boolean structuralOnly;

    @Data
    public static class HistoryItem {
        /** 消息ID（用于多轮硬指代解析 parentId 定位） */
        private String id;
        private String role;
        private String content;
        private String intent;
        private Map<String, String> slots;
        /** 指代目标：指向上一轮被引用消息的 id（如「它」「这个」指向的对象） */
        private String parentId;
    }

    @Data
    public static class ProjectFile {
        private String path;
        private String type; // 'file' | 'directory'
    }
}
