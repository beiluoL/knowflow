package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Architecture-4.3：知识处理结果（纯瞬态候选，处理阶段<strong>零写库</strong>）。
 *
 * <p>状态语义：{@code NEW}=新增候选（接受后新建知识）；{@code EXISTING}=库中已存在同名知识
 * （接受后复用既有实体，绝不重复建实体）。只有用户显式「接受」后才进入 Knowledge Core。</p>
 */
@Data
public class KnowledgeProcessResultVO {

    private Long docId;
    private String docTitle;
    private Long learningNodeId;
    private String learningNodeTitle;

    /** 处理模式：KNOWLEDGE_EXTRACTION / SUMMARY */
    private String mode;
    /** 可追踪：Prompt 版本号。 */
    private String promptVersion;
    /** 可追踪：AI Provider 标识（复用 AiService 模型中心，不写死厂商）。 */
    private String provider;
    /** 可追踪：模型（用户自带 Key 时由 AiService 实际解析，此处为全局默认，可能为 null）。 */
    private String model;
    /** 可追踪：本次处理耗时 ms。 */
    private Long processingTimeMs;
    /** 内容指纹（docId + 正文哈希），供幂等/重复处理审计，不落库。 */
    private String contentFingerprint;
    /** 实际送入模型的字符数。 */
    private Integer processedChars;
    /** 正文是否被截断（长资料只处理前若干段落窗口）。 */
    private Boolean contentTruncated;

    /** 确定性警告（截断、类型/关系归一化、被丢弃的非法项等），空列表表示无警告。 */
    private List<String> warnings = new ArrayList<>();

    /** 面向人的摘要（给人看的；知识结构才是给系统引擎用的）。 */
    private String summary;

    /** 知识候选（去重后）。 */
    private List<ConceptCandidate> concepts = new ArrayList<>();
    /** 关系候选（校验后）。 */
    private List<RelationCandidate> relations = new ArrayList<>();

    // ===== 计数（前端摘要条：新增 X / 已有 Y / 无效 D）=====
    private int conceptTotal;
    private int conceptNew;
    private int conceptExisting;
    private int conceptInvalid;
    private int relationTotal;
    private int relationValid;
    private int relationInvalid;

    @Data
    public static class ConceptCandidate {
        /** 结果内稳定 id（c0..cn），供 UI 勾选/编辑定位。 */
        private String candidateId;
        private String name;
        private String type;
        private String description;
        /** NEW=新增候选 / EXISTING=库中已存在（含 matchedName 提示复用）。 */
        private String status;
        /** status=EXISTING 时的既有实体 id。 */
        private Long existingId;
        /** status=EXISTING 时的既有实体名（与 knowledgeId 同源，不猜名）。 */
        private String matchedName;
    }

    @Data
    public static class RelationCandidate {
        private String candidateId;
        private String source;
        private String target;
        private String relation;
        private String description;
        /** VALID=可接受 / INVALID=非法（引用未知概念、自环、重复等）。 */
        private String status;
        private String invalidReason;
    }
}
