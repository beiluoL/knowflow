package com.knowflow.vo;

import com.knowflow.dto.TechVocabDtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 技术英语对外 VO 集合。
 * 字段命名与前端 DTO 一一对应；不返回未审核音频，不返回无权限的知识目标标题。
 */
public final class TechVocabVOs {

    private TechVocabVOs() {
    }

    /** 列表项 */
    public record TermSummary(Long id,
                              String headword,
                              String termType,
                              String domain,
                              String meaningCn,
                              String fullForm,
                              String className,
                              String ipaUs,
                              String ipaUk,
                              boolean hasVerifiedPronunciation,
                              List<String> pronunciationVariants) {
    }

    /** 发音变体 */
    public record PronunciationVO(Long id,
                                  String variant,
                                  String readingText,
                                  String locale,
                                  String readingMode,
                                  String audioUrl,
                                  String audioProvider,
                                  String licenseNote,
                                  String verificationStatus) {
    }

    /** 概念关联（目标不可访问时不会出现在列表中） */
    public record ConceptLinkVO(String targetType,
                                Long targetId,
                                String relationType,
                                String label,
                                String route) {
    }

    /** 相关术语 */
    public record RelatedTermVO(Long id,
                                String headword,
                                String meaningCn,
                                String domain,
                                String relationType) {
    }

    /** 词条详情 */
    public record TermDetail(Long id,
                             String headword,
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
                             List<TechVocabDtos.WordPart> wordBreakdown,
                             String breakdownNote,
                             String interviewEn,
                             String exampleEn,
                             String exampleCn,
                             List<String> aliases,
                             String contentStatus,
                             String referenceSource,
                             String licenseNote,
                             LocalDateTime reviewedAt,
                             List<PronunciationVO> pronunciations,
                             List<ConceptLinkVO> conceptLinks,
                             List<RelatedTermVO> relatedTerms,
                             Boolean favorite,
                             String learningStatus) {
    }

    /** 用户状态 */
    public record UserStateVO(Long termId,
                              Boolean favorite,
                              String learningStatus,
                              LocalDateTime updatedAt) {
    }

    /** 「我的生词」列表项：词条摘要 + 用户状态 */
    public record MyTermVO(TermSummary term,
                           Boolean favorite,
                           String learningStatus,
                           LocalDateTime updatedAt) {
    }

    /** 筛选统计 */
    public record FiltersVO(long total,
                            Map<String, Long> byType,
                            List<DomainCount> domains,
                            Long myFavoriteCount,
                            Long myLearningCount) {
    }

    /** 领域计数 */
    public record DomainCount(String domain, long count) {
    }

    /** 导入预览条目 */
    public record ImportPreviewItem(int index,
                                    String headword,
                                    String domain,
                                    String action,
                                    String message) {
    }

    /** 导入预览结果 */
    public record ImportPreviewVO(String token,
                                  int newCount,
                                  int updateCount,
                                  int invalidCount,
                                  List<ImportPreviewItem> items,
                                  LocalDateTime expiresAt) {
    }

    /** 导入执行结果 */
    public record ImportResultVO(int created,
                                 int updated,
                                 int skipped,
                                 int invalid,
                                 boolean replayed) {
    }

    /* ============================ P1 词汇训练 ============================ */

    /** 加入复习结果：created=false 表示复用已有复习卡 */
    public record AddReviewResultVO(Long cardId, boolean created) {
    }

    /** 复习队列条目：cardId 为空表示该词尚未加入复习（新词） */
    public record ReviewItemVO(Long termId,
                               Long cardId,
                               String headword,
                               String termType,
                               String domain,
                               String meaningCn,
                               String fullForm,
                               String className,
                               String definitionCn,
                               String ipaUs,
                               String ipaUk,
                               List<PronunciationVO> pronunciations,
                               boolean acronymMode,
                               boolean listeningMode,
                               String learningStatus,
                               boolean favorite,
                               LocalDateTime dueAt,
                               int reviewCount) {
    }

    /** 复习队列 */
    public record ReviewQueueVO(List<ReviewItemVO> items,
                                int dueCount,
                                int newCount,
                                int limit,
                                boolean hasReviewCards) {
    }

    /** 单个技术方向的学习进度 */
    public record DomainProgressVO(String domain, long total, long learned) {
    }

    /** 个人技术英语学习看板（全部来自真实服务端数据） */
    public record VocabProgressVO(long learned,
                                  long dueToday,
                                  long completedToday,
                                  long addedToReview,
                                  long favoriteCount,
                                  long totalTerms,
                                  List<DomainProgressVO> domains) {
    }

    /** 评分回执：dueAt/revision 来自既有 FSRS 调度结果 */
    public record ReviewReceiptVO(Long termId,
                                  Long cardId,
                                  int rating,
                                  String dueAt,
                                  String state,
                                  long revision,
                                  boolean replayed) {
    }

    /* ============================ P2 划词与提词 ============================ */

    /**
     * 划词查询结果。
     * ambiguous=true 表示同一归一化词形存在多个义项（如 CAS），由用户自行选择；
     * rejectedReason 非空表示选区过长 / 跨段落，不执行查询。
     */
    public record LookupResultVO(String query,
                                 String normalized,
                                 String rejectedReason,
                                 boolean ambiguous,
                                 List<TermDetail> matches) {
    }

    /** 候选提取条目（尚未落库） */
    public record ExtractedCandidateVO(String headword,
                                       String termType,
                                       String domain,
                                       String fullForm,
                                       String meaningCn,
                                       String definitionCn,
                                       String sourceContext,
                                       Integer confidence,
                                       String extractor,
                                       Boolean existsInLibrary,
                                       Long duplicateTermId,
                                       String duplicateHeadword) {
    }

    /** 候选提取结果：extractor 如实说明本次是 AI 还是规则 */
    public record ExtractionResultVO(String extractor,
                                     boolean aiAvailable,
                                     String degradationNote,
                                     int totalScanned,
                                     boolean truncated,
                                     List<ExtractedCandidateVO> items) {
    }

    /** 个人 / 管理端候选列表项 */
    public record CandidateVO(Long id,
                              String headword,
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
                              String extractor,
                              String reviewStatus,
                              Long duplicateTermId,
                              Long termId,
                              String reviewNote,
                              Integer occurCount) {
    }

    /** 候选保存回执：saved 为真实新增数量，skipped 为重复跳过数量 */
    public record CandidateSaveResultVO(int saved,
                                        int skipped,
                                        List<CandidateVO> items) {
    }

    /* ============================ V3 专题学习 ============================ */

    /**
     * 专题概览：按现有 domain 聚合，不新建学习路径表。
     * learned = 学习状态非 NEW 的词条数；due = 已加入复习且已到期的词条数。
     */
    public record TopicVO(String domain,
                          long total,
                          long learned,
                          long due) {
    }

    /** 专题内词条：只读展示 + 既有 FSRS 状态，调度仍由 FlashcardFsrsService 决定 */
    public record TopicTermVO(Long termId,
                              String headword,
                              String termType,
                              String meaningCn,
                              String fullForm,
                              String ipaUs,
                              String ipaUk,
                              List<PronunciationVO> pronunciations,
                              boolean hasVerifiedPronunciation,
                              String learningStatus,
                              boolean favorite,
                              boolean hasReviewCard,
                              LocalDateTime dueAt,
                              int reviewCount) {
    }

    /** 专题详情：顺序按词条 id 升序，即词库编写时的由基础到进阶顺序 */
    public record TopicDetailVO(String domain,
                                long total,
                                long learned,
                                long due,
                                List<TopicTermVO> terms) {
    }
}
