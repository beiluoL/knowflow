package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.KgEntity;
import com.knowflow.entity.KnowledgeMastery;
import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.ResourceKnowledgeMapping;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.KgEntityMapper;
import com.knowflow.mapper.KnowledgeMasteryMapper;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.mapper.ResourceKnowledgeMappingMapper;
import com.knowflow.mastery.MasteryComputation;
import com.knowflow.mastery.MasteryConfig;
import com.knowflow.service.impl.MasteryEngine;
import com.knowflow.mastery.MasterySignal;
import com.knowflow.service.KnowledgeMasteryService;
import com.knowflow.service.ResourceKnowledgeService;
import com.knowflow.vo.KnowledgeMasteryDetailVO;
import com.knowflow.vo.KnowledgeMasteryVO;
import com.knowflow.vo.MasteryDiagnosticsVO;
import com.knowflow.vo.SignalContributionVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识点掌握度服务实现（Knowledge Mastery Engine，Phase 2-B）。
 * <p>合成：有效权重归一化的 mastery、N/(N+K) 置信度、遗忘风险、状态机；整行重算保证幂等。</p>
 */
@Slf4j
@Service
public class KnowledgeMasteryServiceImpl implements KnowledgeMasteryService {

    private static final List<String> RES_TYPES = Arrays.asList(
            MasteryConfig.RES_QUIZ, MasteryConfig.RES_CODE_QUESTION, MasteryConfig.RES_MISTAKE,
            MasteryConfig.RES_REVIEW_CARD, MasteryConfig.RES_RECALL_SESSION,
            MasteryConfig.RES_FLASHCARD, MasteryConfig.RES_DOC);

    private final KnowledgeMasteryMapper masteryMapper;
    private final ResourceKnowledgeMappingMapper mappingMapper;
    private final KgEntityMapper kgEntityMapper;
    private final DocCategoryMapper docCategoryMapper;
    private final LearningEventMapper learningEventMapper;
    private final MasteryEngine masteryEngine;
    private final ResourceKnowledgeService resourceKnowledgeService;

    public KnowledgeMasteryServiceImpl(KnowledgeMasteryMapper masteryMapper,
                                       ResourceKnowledgeMappingMapper mappingMapper,
                                       KgEntityMapper kgEntityMapper,
                                       DocCategoryMapper docCategoryMapper,
                                       LearningEventMapper learningEventMapper,
                                       MasteryEngine masteryEngine,
                                       ResourceKnowledgeService resourceKnowledgeService) {
        this.masteryMapper = masteryMapper;
        this.mappingMapper = mappingMapper;
        this.kgEntityMapper = kgEntityMapper;
        this.docCategoryMapper = docCategoryMapper;
        this.learningEventMapper = learningEventMapper;
        this.masteryEngine = masteryEngine;
        this.resourceKnowledgeService = resourceKnowledgeService;
    }

    // ============================================================
    // 事件触发
    // ============================================================

    @Override
    public void processEvent(LearningEvent event) {
        try {
            if (event == null || event.getResourceType() == null || event.getResourceId() == null) {
                return;
            }
            List<Long> kids = resourceKnowledgeService.resolveKnowledgeIds(event.getResourceType(), event.getResourceId());
            if (kids.isEmpty()) {
                log.debug("掌握度引擎：事件无已接受映射，跳过 mastery（事件已落库）。type={}, resId={}",
                        event.getResourceType(), event.getResourceId());
                return;
            }
            for (Long kid : kids) {
                recalc(event.getUserId(), kid);
            }
        } catch (Exception ex) {
            log.warn("掌握度引擎处理事件失败（已忽略，不阻断业务）: eventId={}, err={}",
                    event.getId(), ex.getMessage());
        }
    }

    // ============================================================
    // 重算（独立事务，幂等）
    // ============================================================

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalc(Long userId, Long knowledgeId) {
        try {
            MasteryComputation comp = masteryEngine.compute(userId, knowledgeId);
            if (!comp.hasSignals()) {
                KnowledgeMastery ex = selectRow(userId, knowledgeId);
                if (ex != null) {
                    masteryMapper.deleteById(ex.getId());
                }
                return;
            }
            Synthesis s = synthesize(comp);
            KnowledgeMastery row = selectRow(userId, knowledgeId);
            if (row == null) {
                row = new KnowledgeMastery();
            }
            row.setUserId(userId);
            row.setKnowledgeId(knowledgeId);
            row.setMasteryScore(s.mastery);
            row.setConfidenceScore(s.conf);
            row.setLearningStatus(s.status);
            row.setForgettingRisk(s.risk);
            row.setCorrectCount(comp.getCorrectCount());
            row.setWrongCount(comp.getWrongCount());
            row.setAttemptCount(comp.getAttemptCount());
            row.setReviewCount(comp.getReviewCount());
            row.setRecallCount(comp.getRecallCount());
            row.setRecallAvgScore(comp.getRecallAvgScore());
            row.setCodingAttemptCount(comp.getCodingAttemptCount());
            row.setCodingPassCount(comp.getCodingPassCount());
            row.setMistakeCount(comp.getMistakeCount());
            row.setMistakeMastered(comp.getMistakeMastered());
            row.setConsecutiveCorrect(comp.getConsecutiveCorrect());
            row.setConsecutiveWrong(comp.getConsecutiveWrong());
            row.setLastLearnedAt(comp.getLastLearnedAt());
            row.setLastReviewedAt(comp.getLastReviewedAt());
            row.setLastAssessedAt(comp.getLastAssessedAt());
            row.setNextReviewAt(comp.getNextReviewAt());
            if (row.getId() == null) {
                masteryMapper.insert(row);
            } else {
                masteryMapper.updateById(row);
            }
        } catch (Exception ex) {
            log.warn("重算知识点掌握度失败（已忽略）: userId={}, kid={}, err={}", userId, knowledgeId, ex.getMessage());
        }
    }

    @Override
    public void recalculateUser(Long userId) {
        resourceKnowledgeService.buildAllMappings();
        List<LearningEvent> events = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, userId)
                        .in(LearningEvent::getResourceType, RES_TYPES)
                        .isNotNull(LearningEvent::getResourceId));
        Set<Long> knowledgeIds = new HashSet<>();
        for (LearningEvent e : events) {
            knowledgeIds.addAll(resourceKnowledgeService.resolveKnowledgeIds(e.getResourceType(), e.getResourceId()));
        }
        for (Long kid : knowledgeIds) {
            recalc(userId, kid);
        }
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public List<KnowledgeMasteryVO> listAll(Long userId) {
        List<KnowledgeMastery> rows = masteryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeMastery>().eq(KnowledgeMastery::getUserId, userId));
        return toVos(rows);
    }

    @Override
    public List<KnowledgeMasteryVO> listWeak(Long userId) {
        List<KnowledgeMastery> rows = masteryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeMastery>()
                        .eq(KnowledgeMastery::getUserId, userId)
                        .eq(KnowledgeMastery::getLearningStatus, MasteryConfig.STATUS_WEAK));
        return toVos(rows);
    }

    @Override
    public List<KnowledgeMasteryVO> listReviewRequired(Long userId) {
        List<KnowledgeMastery> rows = masteryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeMastery>()
                        .eq(KnowledgeMastery::getUserId, userId)
                        .eq(KnowledgeMastery::getLearningStatus, MasteryConfig.STATUS_REVIEW_REQUIRED));
        return toVos(rows);
    }

    @Override
    public KnowledgeMasteryDetailVO getDetail(Long userId, Long knowledgeId) {
        KgEntity entity = kgEntityMapper.selectById(knowledgeId);
        MasteryComputation comp = masteryEngine.compute(userId, knowledgeId);
        Synthesis s = comp.hasSignals() ? synthesize(comp) : Synthesis.notStarted();
        KnowledgeMasteryDetailVO vo = new KnowledgeMasteryDetailVO();
        vo.setKnowledgeId(knowledgeId);
        vo.setName(entity == null ? null : entity.getName());
        vo.setType(entity == null ? null : entity.getType());
        vo.setDescription(entity == null ? null : entity.getDescription());
        vo.setCategoryId(entity == null ? null : entity.getCategoryId());
        if (entity != null && entity.getCategoryId() != null) {
            DocCategory cat = docCategoryMapper.selectById(entity.getCategoryId());
            vo.setCategoryName(cat == null ? null : cat.getName());
        }
        vo.setMasteryScore(s.mastery);
        vo.setConfidenceScore(s.conf);
        vo.setLearningStatus(s.status);
        vo.setForgettingRisk(s.risk);
        vo.setWeaknessTypes(s.weaknessTypes);
        vo.setReason(s.reason);
        vo.setExplanation(buildExplanation(comp, s));
        vo.setSignals(s.signals);
        vo.setCorrectCount(comp.getCorrectCount());
        vo.setWrongCount(comp.getWrongCount());
        vo.setAttemptCount(comp.getAttemptCount());
        vo.setReviewCount(comp.getReviewCount());
        vo.setRecallCount(comp.getRecallCount());
        vo.setRecallAvgScore(comp.getRecallAvgScore());
        vo.setCodingAttemptCount(comp.getCodingAttemptCount());
        vo.setCodingPassCount(comp.getCodingPassCount());
        vo.setMistakeCount(comp.getMistakeCount());
        vo.setMistakeMastered(comp.getMistakeMastered());
        vo.setConsecutiveCorrect(comp.getConsecutiveCorrect());
        vo.setConsecutiveWrong(comp.getConsecutiveWrong());
        vo.setLastLearnedAt(comp.getLastLearnedAt());
        vo.setLastReviewedAt(comp.getLastReviewedAt());
        vo.setLastAssessedAt(comp.getLastAssessedAt());
        vo.setNextReviewAt(comp.getNextReviewAt());
        return vo;
    }

    @Override
    public MasteryDiagnosticsVO diagnostics(Long userId) {
        MasteryDiagnosticsVO vo = new MasteryDiagnosticsVO();
        long total = mappingMapper.selectCount(new LambdaQueryWrapper<>());
        long accepted = mappingMapper.selectCount(
                new LambdaQueryWrapper<ResourceKnowledgeMapping>().eq(ResourceKnowledgeMapping::getStatus, MasteryConfig.MAP_STATUS_ACCEPTED));
        long pending = mappingMapper.selectCount(
                new LambdaQueryWrapper<ResourceKnowledgeMapping>().eq(ResourceKnowledgeMapping::getStatus, MasteryConfig.MAP_STATUS_PENDING));
        long rejected = mappingMapper.selectCount(
                new LambdaQueryWrapper<ResourceKnowledgeMapping>().eq(ResourceKnowledgeMapping::getStatus, MasteryConfig.MAP_STATUS_REJECTED));
        vo.setTotalMappings(total);
        vo.setAcceptedMappings(accepted);
        vo.setPendingMappings(pending);
        vo.setRejectedMappings(rejected);

        Set<String> acceptedKeys = new HashSet<>();
        List<ResourceKnowledgeMapping> acceptedMaps = mappingMapper.selectList(
                new LambdaQueryWrapper<ResourceKnowledgeMapping>().eq(ResourceKnowledgeMapping::getStatus, MasteryConfig.MAP_STATUS_ACCEPTED));
        for (ResourceKnowledgeMapping m : acceptedMaps) {
            acceptedKeys.add(key(m.getResourceType(), m.getResourceId()));
        }
        List<LearningEvent> events = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>().in(LearningEvent::getResourceType, RES_TYPES).isNotNull(LearningEvent::getResourceId));
        Set<String> seen = new HashSet<>();
        long unmapped = 0;
        for (LearningEvent e : events) {
            String k = key(e.getResourceType(), e.getResourceId());
            if (seen.add(k) && !acceptedKeys.contains(k)) {
                unmapped++;
            }
        }
        vo.setUnmappedResources(unmapped);
        vo.setNote("引擎仅消费 ACCEPTED 映射；PENDING/无映射事件正常跳过且不阻断业务。文档被 AI 抽取或执行 /recalculate 后映射生效。");
        return vo;
    }

    // ============================================================
    // 合成算法
    // ============================================================

    private Synthesis synthesize(MasteryComputation comp) {
        double wsum = 0.0;
        double csum = 0.0;
        List<SignalContributionVO> signals = new ArrayList<>();
        for (MasterySignal sig : comp.getSignals()) {
            double w = sig.getType().getWeight();
            wsum += w;
            csum += w * sig.getStrength();
            SignalContributionVO c = new SignalContributionVO();
            c.setType(sig.getType().name());
            c.setLabel(sig.getType().getLabel());
            c.setStrength((int) Math.round(sig.getStrength() * 100));
            c.setWeight(w);
            c.setContribution((int) Math.round(w * sig.getStrength() * 100));
            c.setSampleCount(sig.getSampleCount());
            signals.add(c);
        }
        int mastery = wsum == 0 ? 0 : (int) Math.round(csum / wsum * 100);
        int sampleTotal = comp.getSignals().stream().mapToInt(MasterySignal::getSampleCount).sum();
        int conf = (int) Math.round((double) sampleTotal / (sampleTotal + MasteryConfig.CONFIDENCE_K) * 100);

        LocalDateTime lastActivity = latest(comp.getLastLearnedAt(), comp.getLastAssessedAt(), comp.getLastReviewedAt());
        long days = lastActivity == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(lastActivity, LocalDateTime.now()));
        double riskRaw = ((double) days / MasteryConfig.DECAY_PERIOD) * MasteryConfig.DECAY_RISK_WEIGHT
                + comp.getLapseWeight() + (100 - mastery) * 0.2;
        int risk = clamp((int) Math.round(riskRaw), 0, 100);

        String status;
        if (risk >= MasteryConfig.REVIEW_REQUIRED_RISK_THRESHOLD
                || (comp.getNextReviewAt() != null && comp.getNextReviewAt().isBefore(LocalDateTime.now()))) {
            status = MasteryConfig.STATUS_REVIEW_REQUIRED;
        } else if (mastery >= MasteryConfig.MASTERED_THRESHOLD
                && conf >= MasteryConfig.MASTERED_CONFIDENCE_THRESHOLD
                && risk < MasteryConfig.MASTERED_RISK_THRESHOLD) {
            status = MasteryConfig.STATUS_MASTERED;
        } else if (mastery < MasteryConfig.WEAK_THRESHOLD) {
            status = MasteryConfig.STATUS_WEAK;
        } else {
            status = MasteryConfig.STATUS_LEARNING;
        }

        List<String> weakness = new ArrayList<>();
        List<String> reason = new ArrayList<>();
        if (mastery < MasteryConfig.WEAK_THRESHOLD) {
            weakness.add("LOW_MASTERY");
            reason.add("掌握度偏低（" + mastery + "）");
        }
        if (comp.getCodingAttemptCount() > 0
                && (double) comp.getCodingPassCount() / comp.getCodingAttemptCount() < 0.5) {
            weakness.add("CODING_WEAK");
            reason.add("编程通过率偏低");
        }
        if (comp.getRecallCount() > 0 && comp.getRecallAvgScore() < 60) {
            weakness.add("RECALL_WEAK");
            reason.add("主动回忆得分偏低");
        }
        if (risk >= MasteryConfig.REVIEW_REQUIRED_RISK_THRESHOLD) {
            weakness.add("FORGETTING_RISK");
            reason.add("遗忘风险高");
        }
        if (conf < MasteryConfig.MASTERED_CONFIDENCE_THRESHOLD) {
            weakness.add("LOW_CONFIDENCE");
            reason.add("样本不足，置信度偏低（" + conf + "）");
        }
        if (comp.getWrongCount() > 0 && comp.getCorrectCount() < comp.getWrongCount()) {
            weakness.add("HIGH_ERROR_RATE");
            reason.add("答题错误较多");
        }

        Synthesis s = new Synthesis();
        s.mastery = mastery;
        s.conf = conf;
        s.risk = risk;
        s.status = status;
        s.weaknessTypes = weakness;
        s.reason = reason.isEmpty() ? null : String.join("；", reason);
        s.signals = signals;
        return s;
    }

    private String buildExplanation(MasteryComputation comp, Synthesis s) {
        if (!comp.hasSignals()) {
            return "暂无学习信号：该知识点尚无任何答题 / 编程 / 复习 / 主动回忆 / 阅读记录，掌握度未知（非「不会」）。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("掌握度 ").append(s.mastery).append("（置信度 ").append(s.conf)
                .append("%），学习状态 ").append(statusLabel(s.status)).append("。");
        sb.append("基于 ").append(comp.getSignals().size()).append(" 个有效信号维度（缺失信号不计入归一化分母）：");
        for (SignalContributionVO c : s.signals) {
            sb.append(c.getLabel()).append("强度 ").append(c.getStrength())
                    .append("%（权重 ").append(c.getWeight()).append("）→ 贡献 ").append(c.getContribution()).append("分；");
        }
        if (s.reason != null) {
            sb.append("薄弱原因：").append(s.reason).append("。");
        }
        sb.append("遗忘风险 ").append(s.risk).append("/100。");
        return sb.toString();
    }

    // ============================================================
    // 视图转换
    // ============================================================

    private List<KnowledgeMasteryVO> toVos(List<KnowledgeMastery> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> kIds = rows.stream().map(KnowledgeMastery::getKnowledgeId).collect(Collectors.toList());
        List<KgEntity> entities = kgEntityMapper.selectList(
                new LambdaQueryWrapper<KgEntity>().in(KgEntity::getId, kIds));
        Map<Long, KgEntity> entMap = new LinkedHashMap<>();
        for (KgEntity e : entities) {
            entMap.put(e.getId(), e);
        }
        Set<Long> catIds = entities.stream().map(KgEntity::getCategoryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> catNameMap = new LinkedHashMap<>();
        if (!catIds.isEmpty()) {
            List<DocCategory> cats = docCategoryMapper.selectList(
                    new LambdaQueryWrapper<DocCategory>().in(DocCategory::getId, catIds));
            for (DocCategory c : cats) {
                catNameMap.put(c.getId(), c.getName());
            }
        }
        List<KnowledgeMasteryVO> vos = new ArrayList<>();
        for (KnowledgeMastery row : rows) {
            KnowledgeMasteryVO vo = new KnowledgeMasteryVO();
            vo.setKnowledgeId(row.getKnowledgeId());
            KgEntity e = entMap.get(row.getKnowledgeId());
            vo.setName(e == null ? null : e.getName());
            vo.setType(e == null ? null : e.getType());
            vo.setCategoryId(e == null ? null : e.getCategoryId());
            vo.setCategoryName(e != null && e.getCategoryId() != null ? catNameMap.get(e.getCategoryId()) : null);
            vo.setMasteryScore(row.getMasteryScore());
            vo.setConfidenceScore(row.getConfidenceScore());
            vo.setLearningStatus(row.getLearningStatus());
            vo.setForgettingRisk(row.getForgettingRisk());
            vo.setLastLearnedAt(row.getLastLearnedAt());
            vo.setLastReviewedAt(row.getLastReviewedAt());
            vo.setLastAssessedAt(row.getLastAssessedAt());
            vo.setNextReviewAt(row.getNextReviewAt());
            Synthesis s = synthesizeForRow(row);
            vo.setWeaknessTypes(s.weaknessTypes);
            vo.setReason(s.reason);
            vos.add(vo);
        }
        return vos;
    }

    private Synthesis synthesizeForRow(KnowledgeMastery row) {
        MasteryComputation comp = new MasteryComputation();
        comp.setCorrectCount(orZero(row.getCorrectCount()));
        comp.setWrongCount(orZero(row.getWrongCount()));
        comp.setCodingAttemptCount(orZero(row.getCodingAttemptCount()));
        comp.setCodingPassCount(orZero(row.getCodingPassCount()));
        comp.setRecallCount(orZero(row.getRecallCount()));
        comp.setRecallAvgScore(orZero(row.getRecallAvgScore()));
        comp.setMistakeCount(orZero(row.getMistakeCount()));
        comp.setLapseWeight(0);
        int mastery = orZero(row.getMasteryScore());
        int conf = orZero(row.getConfidenceScore());
        int risk = orZero(row.getForgettingRisk());
        List<String> weakness = new ArrayList<>();
        List<String> reason = new ArrayList<>();
        if (MasteryConfig.STATUS_WEAK.equals(row.getLearningStatus())) {
            weakness.add("LOW_MASTERY");
            reason.add("掌握度偏低（" + mastery + "）");
        }
        if (comp.getCodingAttemptCount() > 0 && (double) comp.getCodingPassCount() / comp.getCodingAttemptCount() < 0.5) {
            weakness.add("CODING_WEAK");
            reason.add("编程通过率偏低");
        }
        if (comp.getRecallCount() > 0 && comp.getRecallAvgScore() < 60) {
            weakness.add("RECALL_WEAK");
            reason.add("主动回忆得分偏低");
        }
        if (MasteryConfig.STATUS_REVIEW_REQUIRED.equals(row.getLearningStatus())) {
            weakness.add("FORGETTING_RISK");
            reason.add("遗忘风险高");
        }
        if (conf < MasteryConfig.MASTERED_CONFIDENCE_THRESHOLD) {
            weakness.add("LOW_CONFIDENCE");
            reason.add("样本不足，置信度偏低（" + conf + "）");
        }
        if (comp.getWrongCount() > 0 && comp.getCorrectCount() < comp.getWrongCount()) {
            weakness.add("HIGH_ERROR_RATE");
            reason.add("答题错误较多");
        }
        Synthesis s = new Synthesis();
        s.weaknessTypes = weakness;
        s.reason = reason.isEmpty() ? null : String.join("；", reason);
        return s;
    }

    // ============================================================
    // 工具
    // ============================================================

    private KnowledgeMastery selectRow(Long userId, Long knowledgeId) {
        return masteryMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeMastery>()
                        .eq(KnowledgeMastery::getUserId, userId)
                        .eq(KnowledgeMastery::getKnowledgeId, knowledgeId));
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b, LocalDateTime c) {
        LocalDateTime max = a;
        if (b != null && (max == null || b.isAfter(max))) {
            max = b;
        }
        if (c != null && (max == null || c.isAfter(max))) {
            max = c;
        }
        return max;
    }

    private static String key(String type, Long id) {
        return type + ":" + id;
    }

    private static String statusLabel(String status) {
        switch (status) {
            case MasteryConfig.STATUS_NOT_STARTED:
                return "未学习";
            case MasteryConfig.STATUS_LEARNING:
                return "学习中";
            case MasteryConfig.STATUS_WEAK:
                return "薄弱";
            case MasteryConfig.STATUS_MASTERED:
                return "已掌握";
            case MasteryConfig.STATUS_REVIEW_REQUIRED:
                return "需复习";
            default:
                return status;
        }
    }

    /** 合成结果载体。 */
    @Data
    private static final class Synthesis {
        private int mastery;
        private int conf;
        private int risk;
        private String status;
        private List<String> weaknessTypes;
        private String reason;
        private List<SignalContributionVO> signals;

        static Synthesis notStarted() {
            Synthesis s = new Synthesis();
            s.mastery = 0;
            s.conf = 0;
            s.risk = 0;
            s.status = MasteryConfig.STATUS_NOT_STARTED;
            s.weaknessTypes = new ArrayList<>();
            s.reason = "暂无学习信号";
            s.signals = new ArrayList<>();
            return s;
        }
    }
}
