package com.knowflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.LearningEventType;
import com.knowflow.dto.ScenarioAdminDTO;
import com.knowflow.dto.ScenarioKeyPoint;
import com.knowflow.dto.ScenarioSubmitDTO;
import com.knowflow.entity.LearningScenario;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningScenarioMapper;
import com.knowflow.service.LearningEventService;
import com.knowflow.service.LearningScenarioService;
import com.knowflow.vo.ScenarioAdminVO;
import com.knowflow.vo.ScenarioDetailVO;
import com.knowflow.vo.ScenarioSubmitResultVO;
import com.knowflow.vo.ScenarioSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 场景练习服务实现（Architecture-4.0-4 Scenario Lab）。
 * <p>
 * 评分口径（Deterministic-first，§40-42）：纯关键词覆盖，不含 AI、不含掌握度改写。
 * 规范化规则：小写 + 剔除空白/标点/符号（中文英文统一），做子串包含判定；
 * 任一关键词命中即该要点覆盖。该启发式对「要点标签 + 少量高辨识关键词」足够稳定，
 * 局限性（同义改写 / 长答案噪音词误命中）在执行报告中如实披露。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningScenarioServiceImpl extends ServiceImpl<LearningScenarioMapper, LearningScenario>
        implements LearningScenarioService {

    /** 答案长度上限（字符） */
    private static final int MAX_ANSWER_LENGTH = 10000;

    /** 后台管理校验：标题 / 问题长度上限（对齐 schema VARCHAR 200 / 1000） */
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_QUESTION_LENGTH = 1000;
    /** 后台管理校验：要点数 / 单条关键词长度上限（防畸形超大 payload） */
    private static final int MAX_POINT_COUNT = 30;
    private static final int MAX_KEYWORD_LENGTH = 60;

    private final LearningScenarioMapper scenarioMapper;
    private final LearningEventService eventService;
    private final ObjectMapper objectMapper;

    @Override
    public List<ScenarioSummaryVO> listPublished(Long knowledgeId, Integer difficulty) {
        LambdaQueryWrapper<LearningScenario> qw = new LambdaQueryWrapper<LearningScenario>()
                .eq(LearningScenario::getStatus, 1)
                .orderByAsc(LearningScenario::getDifficulty)
                .orderByDesc(LearningScenario::getId);
        if (knowledgeId != null) {
            qw.eq(LearningScenario::getKnowledgeId, knowledgeId);
        }
        if (difficulty != null && difficulty >= 1 && difficulty <= 3) {
            qw.eq(LearningScenario::getDifficulty, difficulty);
        }
        return scenarioMapper.selectList(qw).stream().map(this::toSummary).collect(Collectors.toList());
    }

    @Override
    public ScenarioDetailVO detail(Long id) {
        LearningScenario scenario = requirePublished(id);
        ScenarioDetailVO vo = new ScenarioDetailVO();
        vo.setId(scenario.getId());
        vo.setTitle(scenario.getTitle());
        vo.setContextText(scenario.getContextText());
        vo.setQuestionText(scenario.getQuestionText());
        vo.setDifficulty(scenario.getDifficulty());
        vo.setKnowledgeId(scenario.getKnowledgeId());
        vo.setPointLabels(parseKeyPoints(scenario.getKeyPoints()).stream()
                .map(ScenarioKeyPoint::getLabel)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    public ScenarioSubmitResultVO submit(Long userId, Long scenarioId, ScenarioSubmitDTO dto) {
        LearningScenario scenario = requirePublished(scenarioId);
        String answer = dto != null && dto.getAnswer() != null ? dto.getAnswer().trim() : "";
        if (answer.isEmpty()) {
            throw new BusinessException(400, "作答不能为空，请描述你的排查/回答思路");
        }
        if (answer.length() > MAX_ANSWER_LENGTH) {
            throw new BusinessException(400, "作答过长（上限 " + MAX_ANSWER_LENGTH + " 字符）");
        }
        List<ScenarioKeyPoint> points = parseKeyPoints(scenario.getKeyPoints());
        if (points.isEmpty()) {
            throw new BusinessException("场景要点配置为空，请联系管理员检查：" + scenario.getTitle());
        }

        // 确定性覆盖评分：对每条要点，任一个关键词命中即覆盖
        String normalizedAnswer = normalize(answer);
        List<String> matchedLabels = new ArrayList<>();
        List<String> missedLabels = new ArrayList<>();
        for (ScenarioKeyPoint point : points) {
            boolean hit = false;
            for (String keyword : point.getKeywords()) {
                String kw = normalize(keyword);
                if (!kw.isEmpty() && normalizedAnswer.contains(kw)) {
                    hit = true;
                    break;
                }
            }
            (hit ? matchedLabels : missedLabels).add(point.getLabel());
        }
        int total = points.size();
        int matched = matchedLabels.size();
        double coverage = total == 0 ? 0.0 : (double) matched / total;
        int score = (int) Math.round(coverage * 100);

        ScenarioSubmitResultVO result = new ScenarioSubmitResultVO();
        result.setScenarioId(scenario.getId());
        result.setTitle(scenario.getTitle());
        result.setTotalPoints(total);
        result.setMatchedPoints(matched);
        result.setCoverage(coverage);
        result.setScore(score);
        result.setComment(commentOf(coverage));
        result.setMatchedLabels(matchedLabels);
        result.setMissedLabels(missedLabels);
        result.setRecorded(Boolean.TRUE);

        // 学习行为事件统一落库（REQUIRES_NEW，事件写失败只告警不影响主流程）
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scenarioId", scenario.getId());
        metadata.put("scenarioTitle", scenario.getTitle());
        metadata.put("difficulty", scenario.getDifficulty());
        metadata.put("coverage", coverage);
        metadata.put("score", score);
        metadata.put("matched", matched);
        metadata.put("total", total);
        metadata.put("answerLength", answer.length());
        // Architecture-4.2 §10：场景自带的真实知识点写入事件，供 Evidence 绑定知识点上下文
        // （只在场景确实配置了知识点时写入，未配置则不写，禁止回退猜测）
        if (scenario.getKnowledgeId() != null) {
            metadata.put("knowledgeId", scenario.getKnowledgeId());
        }
        if (dto.getDurationSeconds() != null) {
            metadata.put("durationSeconds", dto.getDurationSeconds());
        }
        eventService.record(userId, LearningEventType.SCENARIO_COMPLETED, "SCENARIO",
                scenario.getId(), metadata);
        return result;
    }

    // ==================== 后台管理（ADMIN，Architecture-4.0-6） ====================

    @Override
    public List<ScenarioAdminVO> adminList(String keyword, Integer status) {
        LambdaQueryWrapper<LearningScenario> qw = new LambdaQueryWrapper<LearningScenario>()
                .orderByDesc(LearningScenario::getId);
        if (StrUtil.isNotBlank(keyword)) {
            qw.and(w -> w.like(LearningScenario::getTitle, keyword)
                    .or().like(LearningScenario::getQuestionText, keyword));
        }
        if (status != null && (status == 0 || status == 1)) {
            qw.eq(LearningScenario::getStatus, status);
        }
        return scenarioMapper.selectList(qw).stream().map(this::toAdminVO).collect(Collectors.toList());
    }

    @Override
    public ScenarioAdminVO adminDetail(Long id) {
        return toAdminVO(requireAny(id));
    }

    @Override
    public ScenarioAdminVO adminCreate(ScenarioAdminDTO dto) {
        LearningScenario entity = toEntity(dto);
        scenarioMapper.insert(entity);
        return toAdminVO(entity);
    }

    @Override
    public ScenarioAdminVO adminUpdate(Long id, ScenarioAdminDTO dto) {
        requireAny(id);
        LearningScenario entity = toEntity(dto);
        entity.setId(id);
        scenarioMapper.updateById(entity);
        return toAdminVO(requireAny(id));
    }

    @Override
    public void adminRemove(Long id) {
        requireAny(id);
        // @TableLogic：deleteById 落为 deleted=1（learning_event 历史作答不受影响）
        scenarioMapper.deleteById(id);
    }

    // ==================== 私有工具 ====================

    private LearningScenario requirePublished(Long id) {
        LearningScenario scenario = scenarioMapper.selectById(id);
        if (scenario == null || scenario.getStatus() == null || scenario.getStatus() != 1) {
            throw new BusinessException(404, "场景不存在或已下架");
        }
        return scenario;
    }

    /** 管理端取任意场景（含下架）：不存在或已逻辑删除 → 404（@TableLogic 已过滤 deleted=1）。 */
    private LearningScenario requireAny(Long id) {
        LearningScenario scenario = scenarioMapper.selectById(id);
        if (scenario == null) {
            throw new BusinessException(404, "场景不存在");
        }
        return scenario;
    }

    /** 管理端 DTO → 实体（校验失败抛 400；contextText 为 null 时落空串以便覆盖式清空）。 */
    private LearningScenario toEntity(ScenarioAdminDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String title = StrUtil.trimToEmpty(dto.getTitle());
        if (title.isEmpty()) {
            throw new BusinessException(400, "场景标题不能为空");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(400, "场景标题过长（上限 " + MAX_TITLE_LENGTH + " 字符）");
        }
        String questionText = StrUtil.trimToEmpty(dto.getQuestionText());
        if (questionText.isEmpty()) {
            throw new BusinessException(400, "场景问题（questionText）不能为空");
        }
        if (questionText.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(400, "场景问题过长（上限 " + MAX_QUESTION_LENGTH + " 字符）");
        }
        List<ScenarioKeyPoint> points = validateAndTrimKeyPoints(dto.getKeyPoints());
        int difficulty = dto.getDifficulty() != null ? dto.getDifficulty() : 1;
        if (difficulty < 1 || difficulty > 3) {
            throw new BusinessException(400, "难度须为 1-3");
        }
        int status = dto.getStatus() != null ? dto.getStatus() : 1;
        if (status != 0 && status != 1) {
            throw new BusinessException(400, "状态须为 0（下架）或 1（上架）");
        }

        LearningScenario entity = new LearningScenario();
        entity.setTitle(title);
        entity.setContextText(StrUtil.trimToEmpty(dto.getContextText()));
        entity.setQuestionText(questionText);
        entity.setKeyPoints(writeKeyPoints(points));
        entity.setDifficulty(difficulty);
        entity.setKnowledgeId(dto.getKnowledgeId());
        entity.setStatus(status);
        return entity;
    }

    /** 校验并归一化要点清单：每条 label 必填且不超长、每个要点至少 1 个非空关键词。 */
    private List<ScenarioKeyPoint> validateAndTrimKeyPoints(List<ScenarioKeyPoint> rawPoints) {
        if (rawPoints == null || rawPoints.isEmpty()) {
            throw new BusinessException(400, "要点清单（keyPoints）至少需要 1 条");
        }
        if (rawPoints.size() > MAX_POINT_COUNT) {
            throw new BusinessException(400, "要点过多（上限 " + MAX_POINT_COUNT + " 条）");
        }
        List<ScenarioKeyPoint> cleaned = new ArrayList<>();
        for (int i = 0; i < rawPoints.size(); i++) {
            ScenarioKeyPoint point = rawPoints.get(i);
            String label = point == null ? null : StrUtil.trimToNull(point.getLabel());
            if (label == null) {
                throw new BusinessException(400, "第 " + (i + 1) + " 条要点的名称（label）不能为空");
            }
            if (label.length() > 100) {
                throw new BusinessException(400, "第 " + (i + 1) + " 条要点名称过长（上限 100 字符）");
            }
            List<String> keywords = point.getKeywords() == null ? new ArrayList<>() : point.getKeywords();
            List<String> cleanedKeywords = new ArrayList<>();
            for (String kw : keywords) {
                String k = StrUtil.trimToNull(kw);
                if (k == null) {
                    continue;
                }
                if (k.length() > MAX_KEYWORD_LENGTH) {
                    throw new BusinessException(400, "第 " + (i + 1) + " 条要点的关键词过长（上限 "
                            + MAX_KEYWORD_LENGTH + " 字符）：" + k);
                }
                cleanedKeywords.add(k);
            }
            if (cleanedKeywords.isEmpty()) {
                throw new BusinessException(400, "第 " + (i + 1) + " 条要点至少需要一个命中关键词");
            }
            ScenarioKeyPoint cp = new ScenarioKeyPoint();
            cp.setLabel(label);
            cp.setKeywords(cleanedKeywords);
            cleaned.add(cp);
        }
        return cleaned;
    }

    /** 序列化要点清单为库内 JSON；失败属配置错误抛 400。 */
    private String writeKeyPoints(List<ScenarioKeyPoint> points) {
        try {
            return objectMapper.writeValueAsString(points);
        } catch (Exception e) {
            log.error("序列化场景要点 JSON 失败", e);
            throw new BusinessException(400, "要点清单格式不合法，无法保存");
        }
    }

    private ScenarioAdminVO toAdminVO(LearningScenario s) {
        ScenarioAdminVO vo = new ScenarioAdminVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setContextText(s.getContextText());
        vo.setQuestionText(s.getQuestionText());
        vo.setKeyPoints(parseKeyPoints(s.getKeyPoints()));
        vo.setDifficulty(s.getDifficulty() != null ? s.getDifficulty() : 1);
        vo.setKnowledgeId(s.getKnowledgeId());
        vo.setStatus(s.getStatus() != null ? s.getStatus() : 1);
        vo.setCreateTime(s.getCreateTime());
        vo.setUpdateTime(s.getUpdateTime());
        return vo;
    }

    private ScenarioSummaryVO toSummary(LearningScenario s) {
        ScenarioSummaryVO vo = new ScenarioSummaryVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setQuestionText(s.getQuestionText());
        vo.setDifficulty(s.getDifficulty() != null ? s.getDifficulty() : 1);
        vo.setKnowledgeId(s.getKnowledgeId());
        vo.setPointCount(parseKeyPoints(s.getKeyPoints()).size());
        return vo;
    }

    private List<ScenarioKeyPoint> parseKeyPoints(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<ScenarioKeyPoint> points = objectMapper.readValue(json, new TypeReference<List<ScenarioKeyPoint>>() {});
            return points == null ? new ArrayList<>() : points;
        } catch (Exception e) {
            log.error("解析场景要点 JSON 失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    /** 确定性覆盖评分规范化：小写 + 剔除空白/标点/符号（中文英文统一为连续子串比较）。 */
    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase().replaceAll("[\\s\\p{P}\\p{S}]+", "");
    }

    /** 确定性分档点评（覆盖度阈值驱动，非 AI 生成）。 */
    private String commentOf(double coverage) {
        if (coverage >= 1.0) {
            return "要点全覆盖：表达完整，可进入下一个场景练习。";
        }
        if (coverage >= 0.7) {
            return "已覆盖大部分要点：主干思路正确，对照「未覆盖」清单补强细节。";
        }
        if (coverage >= 0.4) {
            return "仅覆盖部分要点：思路有雏形但关键环节缺失，建议先复习资料再重答一次。";
        }
        return "要点覆盖不足：建议先回到资料/闪卡复习，再回到本场景重答。";
    }
}
