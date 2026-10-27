package com.knowflow.knowledge;

import java.util.Locale;
import java.util.Set;

/**
 * Architecture-4.3：知识处理确定性规范化工具（纯静态、无状态、可单测）。
 *
 * <p>类型 / 关系词表与 KnowledgeService（A-RAG-04）保持一致，是知识库既有词汇，不扩展 taxonomy：
 * 实体类型 {@code CONCEPT/TECHNIQUE/TERM/PRINCIPLE/TOOL/OTHER}，
 * 关系类型 {@code RELATED_TO/PREREQUISITE/IS_A/PART_OF/USES/CONTRASTS}。</p>
 *
 * <p>为什么独立副本而不是复用 KnowledgeService 的 private 方法：
 * 4.3 采取「最小改动」原则，不重构 A-RAG-04（降低爆炸半径）；两者归一语义完全一致，已在文档中标注后续可收敛。</p>
 */
public final class KnowledgeNormalizer {

    /** 实体类型白名单（kg_entity.type 注释词表）。 */
    public static final Set<String> VALID_TYPES =
            Set.of("CONCEPT", "TECHNIQUE", "TERM", "PRINCIPLE", "TOOL", "OTHER");

    /** 关系类型白名单（kg_relation.relation 注释词表）。 */
    public static final Set<String> VALID_RELATIONS =
            Set.of("RELATED_TO", "PREREQUISITE", "IS_A", "PART_OF", "USES", "CONTRASTS");

    public static final int MAX_NAME = 200;
    public static final int MAX_DESC = 500;
    public static final int MAX_CONCEPTS = 20;
    public static final int MAX_RELATIONS = 60;

    private KnowledgeNormalizer() {
    }

    /**
     * 名称归一化（用于同源去重与库比对）：trim + 小写 + 空白折叠。
     * 中文不受小写影响；不删标点（避免误合并带标点术语）。
     */
    public static String normalizeName(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** 实体类型规范化：别名/大小写容忍，未知一律归 CONCEPT（与 A-RAG-04 一致）。 */
    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "CONCEPT";
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "TECHNIQUE", "TECH" -> "TECHNIQUE";
            case "TERM" -> "TERM";
            case "PRINCIPLE" -> "PRINCIPLE";
            case "TOOL" -> "TOOL";
            case "OTHER" -> "OTHER";
            default -> "CONCEPT";
        };
    }

    /** 关系类型规范化：别名/大小写容忍，未知一律归 RELATED_TO（与 A-RAG-04 一致）。 */
    public static String normalizeRelation(String relation) {
        if (relation == null || relation.isBlank()) {
            return "RELATED_TO";
        }
        return switch (relation.trim().toUpperCase(Locale.ROOT)) {
            case "PREREQUISITE" -> "PREREQUISITE";
            case "IS_A", "ISA", "IS-A" -> "IS_A";
            case "PART_OF", "PARTOF", "PART-OF", "CONTAINS", "HAS_COMPONENT", "HAS" -> "PART_OF";
            case "USES", "USE", "DEPENDS_ON", "RELY_ON" -> "USES";
            case "CONTRASTS", "CONTRAST", "DIFFERS_FROM" -> "CONTRASTS";
            case "RELATED_TO", "RELATED" -> "RELATED_TO";
            default -> "RELATED_TO";
        };
    }

    /** 长度截断。 */
    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
