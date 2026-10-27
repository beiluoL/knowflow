package com.knowflow.dto;

import java.util.List;

/**
 * 技术英语管理端 DTO。
 * 使用 record 承载请求体；字段校验在 TechVocabService 中集中完成，
 * 保证创建、编辑、导入预览三条入口使用同一套规则。
 */
public final class TechVocabDtos {

    private TechVocabDtos() {
    }

    /** 全称逐词拆解的一项 */
    public record WordPart(String word, String meaningCn) {
    }

    /** 发音变体输入 */
    public record PronunciationInput(String variant,
                                     String readingText,
                                     String locale,
                                     String readingMode,
                                     String audioUrl,
                                     String audioProvider,
                                     String licenseNote,
                                     String verificationStatus) {
    }

    /** 词条创建/编辑输入 */
    public record TermInput(String headword,
                            String termType,
                            String domain,
                            String senseKey,
                            String fullForm,
                            String className,
                            String partOfSpeech,
                            String ipaUs,
                            String ipaUk,
                            String meaningCn,
                            String definitionCn,
                            String definitionEn,
                            List<WordPart> wordBreakdown,
                            String breakdownNote,
                            String interviewEn,
                            String exampleEn,
                            String exampleCn,
                            List<String> aliases,
                            String contentStatus,
                            String referenceSource,
                            String licenseNote,
                            List<PronunciationInput> pronunciations) {
    }

    /** 用户状态局部更新：字段为 null 表示不修改 */
    public record UserStateInput(Boolean favorite, String learningStatus) {
    }

    /** 导入预览请求 */
    public record ImportRequest(List<TermInput> terms) {
    }

    /** 导入确认请求 */
    public record ImportConfirmRequest(String token) {
    }

    /**
     * 词汇复习评分输入。
     * revision / settingsRevision 由服务端读取，避免客户端持有过期排程；
     * requestId 复用既有 FSRS 幂等协议，重复提交不会重复记分。
     */
    public record ReviewInput(Integer rating, String requestId, String sessionId) {
    }

    /* ============================ P2 划词与提词 ============================ */

    /**
     * 术语提取请求。
     * 只允许三种来源之一：正文 text、知识库文档 documentId、个人笔记 noteId；
     * scope=SELECTION 时以 text 为准，scope=ARTICLE 时由服务端读取受权限保护的正文。
     */
    public record ExtractInput(String text,
                               Long documentId,
                               Long noteId,
                               String scope,
                               List<String> types,
                               Integer limit,
                               Boolean useAi) {
    }

    /** 候选词条写入输入（用户确认后的个人候选） */
    public record CandidateInput(String headword,
                                 String termType,
                                 String domain,
                                 String fullForm,
                                 String meaningCn,
                                 String definitionCn,
                                 String sourceType,
                                 Long sourceId,
                                 String sourceTitle,
                                 String sourceContext,
                                 Integer confidence,
                                 String extractor) {
    }

    /** 候选批量保存请求 */
    public record CandidateSaveRequest(List<CandidateInput> candidates) {
    }

    /** 候选审核输入：approve=false 时为拒绝；publish=true 时审核通过即发布 */
    public record CandidateReviewInput(Boolean approve, Boolean publish, String meaningCn,
                                       String domain, String reviewNote) {
    }
}
