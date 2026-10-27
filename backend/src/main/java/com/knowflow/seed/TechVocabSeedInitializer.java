package com.knowflow.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.TechVocabConceptLink;
import com.knowflow.entity.TechVocabPronunciation;
import com.knowflow.entity.TechVocabTerm;
import com.knowflow.mapper.TechVocabConceptLinkMapper;
import com.knowflow.mapper.TechVocabPronunciationMapper;
import com.knowflow.mapper.TechVocabTermMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技术英语 P0 种子词库初始化。
 *
 * 规则：
 * - 只新增缺失词条，不覆盖管理员已发布内容；
 * - 不写任何用户收藏/学习状态；
 * - 词条关系在全部词条落库后按 (headword, domain) 解析为稳定 ID；
 * - 初始化失败只记录错误，不阻断其他模块启动。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class TechVocabSeedInitializer implements ApplicationRunner {

    private final TechVocabTermMapper termMapper;
    private final TechVocabPronunciationMapper pronunciationMapper;
    private final TechVocabConceptLinkMapper linkMapper;
    private final ObjectMapper objectMapper;

    /** 按批次顺序加载；每个文件都幂等，已存在词条只跳过不覆盖 */
    private static final List<String> SEED_FILES = List.of(
            "seed/tech-vocab.seed.json",
            "seed/tech-vocab.seed.p2.json",
            // V3 扩充：导入发布后纳入种子，新库可直接建库，已有库只补关系不覆盖
            "seed/tech-vocab.seed.v3.b1.json",
            "seed/tech-vocab.seed.v3.b2.json",
            "seed/tech-vocab.seed.v3.b3.json",
            "seed/tech-vocab.seed.v3.b4.json",
            "seed/tech-vocab.seed.v3.b5.json");

    @Value("${knowflow.tech-english.seed-enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        int created = 0;
        int skipped = 0;
        int linked = 0;
        for (String path : SEED_FILES) {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (!resource.exists()) {
                    log.warn("技术英语种子文件不存在，跳过：{}", path);
                    continue;
                }
                SeedFile file = objectMapper.readValue(resource.getInputStream(), SeedFile.class);
                if (file == null || file.terms == null || file.terms.isEmpty()) {
                    continue;
                }
                int batchCreated = 0;
                int batchSkipped = 0;
                for (SeedTerm term : file.terms) {
                    if (term == null || term.headword == null || term.domain == null) {
                        continue;
                    }
                    if (seedTerm(term)) {
                        batchCreated++;
                    } else {
                        batchSkipped++;
                    }
                }
                int batchLinked = seedRelations(file.terms);
                created += batchCreated;
                skipped += batchSkipped;
                linked += batchLinked;
                log.info("技术英语种子批次 {}：新增 {} 条，已存在跳过 {} 条，建立关联 {} 条",
                        path, batchCreated, batchSkipped, batchLinked);
            } catch (Exception e) {
                // 单批次失败不阻断后续批次与其他模块启动
                log.error("技术英语种子批次初始化失败（不影响其他模块启动）：{}", path, e);
            }
        }
        log.info("技术英语种子完成：累计新增 {} 条，已存在跳过 {} 条，建立关联 {} 条", created, skipped, linked);
    }

    /** 返回 true 表示本次新建 */
    private boolean seedTerm(SeedTerm seed) {
        String headword = seed.headword.trim();
        String normalizedKey = normalizeKey(headword);
        String domain = seed.domain.trim().toUpperCase(Locale.ROOT);
        String senseKey = seed.senseKey == null || seed.senseKey.isBlank()
                ? "default" : seed.senseKey.trim().toLowerCase(Locale.ROOT);
        long exists = termMapper.selectCount(new LambdaQueryWrapper<TechVocabTerm>()
                .eq(TechVocabTerm::getNormalizedKey, normalizedKey)
                .eq(TechVocabTerm::getDomain, domain)
                .eq(TechVocabTerm::getSenseKey, senseKey));
        if (exists > 0) {
            return false;
        }
        TechVocabTerm term = new TechVocabTerm();
        term.setHeadword(headword);
        term.setNormalizedKey(normalizedKey);
        term.setTermType(seed.termType == null ? "WORD" : seed.termType.trim().toUpperCase(Locale.ROOT));
        term.setDomain(domain);
        term.setSenseKey(senseKey);
        term.setFullForm(trimToNull(seed.fullForm));
        term.setClassName(trimToNull(seed.className));
        term.setPartOfSpeech(trimToNull(seed.partOfSpeech));
        term.setIpaUs(trimToNull(seed.ipaUs));
        term.setIpaUk(trimToNull(seed.ipaUk));
        term.setMeaningCn(seed.meaningCn == null ? "" : seed.meaningCn.trim());
        term.setDefinitionCn(trimToNull(seed.definitionCn));
        term.setDefinitionEn(trimToNull(seed.definitionEn));
        term.setWordBreakdown(writeBreakdown(seed.wordBreakdown));
        term.setBreakdownNote(trimToNull(seed.breakdownNote));
        term.setInterviewEn(trimToNull(seed.interviewEn));
        term.setExampleEn(trimToNull(seed.exampleEn));
        term.setExampleCn(trimToNull(seed.exampleCn));
        term.setAliases(joinAliases(seed.aliases));
        term.setContentStatus(seed.contentStatus == null
                ? "PUBLISHED" : seed.contentStatus.trim().toUpperCase(Locale.ROOT));
        term.setReferenceSource(trimToNull(seed.referenceSource));
        term.setLicenseNote(trimToNull(seed.licenseNote));
        term.setReviewedAt("PUBLISHED".equals(term.getContentStatus())
                || "REVIEWED".equals(term.getContentStatus()) ? LocalDateTime.now() : null);
        try {
            termMapper.insert(term);
        } catch (DuplicateKeyException race) {
            // 多实例同时启动时由另一实例写入，按不存在处理，避免覆盖
            return false;
        }
        insertPronunciations(term.getId(), seed.pronunciations, term.getTermType(), headword);
        return true;
    }

    private void insertPronunciations(Long termId, List<SeedPronunciation> inputs,
                                      String termType, String headword) {
        List<SeedPronunciation> rows = inputs;
        if (rows == null || rows.isEmpty()) {
            rows = "ACRONYM".equals(termType)
                    // 缩写未登记逐字母读法时保持 PENDING：不猜测该缩写怎么读
                    ? List.of(
                            new SeedPronunciation("ABBR", null, "en-US", "LETTER_BY_LETTER", "PENDING"),
                            new SeedPronunciation("FULL_FORM", null, "en-US", "SPOKEN_WORD", "PENDING"))
                    // 单词/短语的朗读文本就是词条本身，可确定，登记为 VERIFIED；
                    // 仍不写入音标、不生成录音，播放继续走浏览器合成语音
                    : List.of(
                            new SeedPronunciation("WORD_US", headword, "en-US", "SPOKEN_WORD", "VERIFIED"),
                            new SeedPronunciation("WORD_UK", headword, "en-GB", "SPOKEN_WORD", "VERIFIED"));
        }
        for (SeedPronunciation p : rows) {
            if (p == null || p.variant == null) {
                continue;
            }
            TechVocabPronunciation row = new TechVocabPronunciation();
            row.setTermId(termId);
            row.setVariant(p.variant.trim().toUpperCase(Locale.ROOT));
            row.setReadingText(trimToNull(p.readingText));
            row.setLocale(trimToNull(p.locale));
            row.setReadingMode(trimToNull(p.readingMode));
            row.setAudioUrl(trimToNull(p.audioUrl));
            row.setAudioProvider(trimToNull(p.audioProvider));
            row.setLicenseNote(trimToNull(p.licenseNote));
            row.setVerificationStatus(p.verificationStatus == null
                    ? "PENDING" : p.verificationStatus.trim().toUpperCase(Locale.ROOT));
            try {
                pronunciationMapper.insert(row);
            } catch (DuplicateKeyException race) {
                log.warn("技术英语发音变体重复，跳过：{} {}", headword, row.getVariant());
            }
        }
    }

    private int seedRelations(List<SeedTerm> terms) {
        if (terms == null || terms.isEmpty()) {
            return 0;
        }
        // 按归一化键 + 领域建立索引；同一键多义时取最新发布的一条
        List<TechVocabTerm> all = termMapper.selectList(new LambdaQueryWrapper<TechVocabTerm>()
                .select(TechVocabTerm::getId, TechVocabTerm::getNormalizedKey, TechVocabTerm::getDomain)
                .eq(TechVocabTerm::getContentStatus, "PUBLISHED"));
        Map<String, Long> index = all.stream().collect(Collectors.toMap(
                t -> t.getNormalizedKey() + "|" + t.getDomain(),
                TechVocabTerm::getId,
                (a, b) -> a));
        int created = 0;
        for (SeedTerm seed : terms) {
            if (seed == null || seed.relations == null || seed.relations.isEmpty()) {
                continue;
            }
            String sourceKey = normalizeKey(seed.headword) + "|"
                    + seed.domain.trim().toUpperCase(Locale.ROOT);
            Long sourceId = index.get(sourceKey);
            if (sourceId == null) {
                continue;
            }
            for (SeedRelation relation : seed.relations) {
                if (relation == null || relation.targetHeadword == null || relation.targetDomain == null) {
                    continue;
                }
                String targetKey = normalizeKey(relation.targetHeadword) + "|"
                        + relation.targetDomain.trim().toUpperCase(Locale.ROOT);
                Long targetId = index.get(targetKey);
                if (targetId == null || targetId.equals(sourceId)) {
                    continue;
                }
                String relationType = relation.relationType == null
                        ? "RELATED" : relation.relationType.trim().toUpperCase(Locale.ROOT);
                long exists = linkMapper.selectCount(new LambdaQueryWrapper<TechVocabConceptLink>()
                        .eq(TechVocabConceptLink::getTermId, sourceId)
                        .eq(TechVocabConceptLink::getTargetType, "TERM")
                        .eq(TechVocabConceptLink::getTargetId, targetId)
                        .eq(TechVocabConceptLink::getRelationType, relationType));
                if (exists > 0) {
                    continue;
                }
                TechVocabConceptLink link = new TechVocabConceptLink();
                link.setTermId(sourceId);
                link.setTargetType("TERM");
                link.setTargetId(targetId);
                link.setRelationType(relationType);
                try {
                    linkMapper.insert(link);
                    created++;
                } catch (DuplicateKeyException race) {
                    log.debug("技术英语词条关系已存在，跳过");
                }
            }
        }
        return created;
    }

    private String writeBreakdown(List<WordPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parts);
        } catch (Exception e) {
            log.warn("技术英语逐词拆解序列化失败，按空处理");
            return null;
        }
    }

    private String joinAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        String joined = aliases.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private String normalizeKey(String headword) {
        return headword.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /* ============================ 种子 JSON 结构 ============================ */

    static final class SeedFile {
        public String note;
        public List<SeedTerm> terms;
    }

    static final class SeedTerm {
        public String headword;
        public String termType;
        public String domain;
        public String senseKey;
        public String fullForm;
        public String className;
        public String partOfSpeech;
        public String ipaUs;
        public String ipaUk;
        public String meaningCn;
        public String definitionCn;
        public String definitionEn;
        public List<WordPart> wordBreakdown;
        public String breakdownNote;
        public String interviewEn;
        public String exampleEn;
        public String exampleCn;
        public List<String> aliases;
        public String contentStatus;
        public String referenceSource;
        public String licenseNote;
        public List<SeedPronunciation> pronunciations;
        public List<SeedRelation> relations;
    }

    static final class WordPart {
        public String word;
        public String meaningCn;
    }

    static final class SeedPronunciation {
        public String variant;
        public String readingText;
        public String locale;
        public String readingMode;
        public String audioUrl;
        public String audioProvider;
        public String licenseNote;
        public String verificationStatus;

        SeedPronunciation() {
        }

        SeedPronunciation(String variant, String readingText, String locale,
                          String readingMode, String verificationStatus) {
            this.variant = variant;
            this.readingText = readingText;
            this.locale = locale;
            this.readingMode = readingMode;
            this.verificationStatus = verificationStatus;
        }
    }

    static final class SeedRelation {
        public String targetHeadword;
        public String targetDomain;
        public String relationType;
    }
}
