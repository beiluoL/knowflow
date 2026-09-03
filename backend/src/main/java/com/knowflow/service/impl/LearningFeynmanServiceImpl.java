package com.knowflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.knowflow.common.LearningEventType;
import com.knowflow.dto.FeynmanSubmitDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.LearningEventService;
import com.knowflow.service.LearningFeynmanService;
import com.knowflow.vo.FeynmanCriterionVO;
import com.knowflow.vo.FeynmanDimensionResultVO;
import com.knowflow.vo.FeynmanDimensionVO;
import com.knowflow.vo.FeynmanRubricVO;
import com.knowflow.vo.FeynmanSubmitResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 费曼表达四维评分实现（Architecture-4.0-7）。
 * <p>
 * 口径（Deterministic-first，§40）：每维 4 条「可观察自检准则」，作者逐条核验勾选，
 * 维度得分 = round(勾选数/4×100)，总分 = round(全部勾选数/16×100)。Accuracy 无机器可验证的
 * 参照系，以「作者对照资料自检」为据——这在 rubric 文案与执行报告中如实披露，不伪装语义评分。
 * 事件：FEYNMAN_EXPRESSED（resourceType=FEYNMAN，resourceId=0：无内容实体，按 user+type+时间查询），
 * 元数据携带 topic/overall/四维 score/checked/total，不直写掌握度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningFeynmanServiceImpl implements LearningFeynmanService {

    /** 表达主题长度上限（字符）。 */
    private static final int MAX_TOPIC_LENGTH = 200;
    /** 解释正文长度上限（字符）。 */
    private static final int MAX_EXPLANATION_LENGTH = 10000;
    /** 每维固定准则数（计分分母）。 */
    private static final int CRITERIA_PER_DIM = 4;
    /** 维度总数。 */
    private static final int DIM_COUNT = 4;

    private final LearningEventService eventService;

    @Override
    public FeynmanRubricVO rubric() {
        FeynmanRubricVO vo = new FeynmanRubricVO();
        vo.setDimensions(dimensions());
        vo.setScoringNote("四维各 4 条自检准则：评分 = 勾选数 / 4。"
                + "「准确性」无法由系统无参照验证，以你对照资料逐条核对的自检为准；"
                + "「清晰度 / 结构」请在提交前通读（必要时朗读）后核验。分数作为结构化表达证据入库，不直写掌握度。");
        return vo;
    }

    @Override
    public FeynmanSubmitResultVO submit(Long userId, FeynmanSubmitDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String topic = StrUtil.trimToEmpty(dto.getTopic());
        if (topic.isEmpty()) {
            throw new BusinessException(400, "请填写表达主题");
        }
        if (topic.length() > MAX_TOPIC_LENGTH) {
            throw new BusinessException(400, "表达主题过长（上限 " + MAX_TOPIC_LENGTH + " 字符）");
        }
        String explanation = StrUtil.trimToEmpty(dto.getExplanation());
        if (explanation.isEmpty()) {
            throw new BusinessException(400, "请先写下你的解释正文");
        }
        if (explanation.length() > MAX_EXPLANATION_LENGTH) {
            throw new BusinessException(400, "解释正文过长（上限 " + MAX_EXPLANATION_LENGTH + " 字符）");
        }

        // 白名单：rubric 中所有准则 id → 所在维度下标；未知 id 忽略、同一准则只计一次（Set 去重，确定性容错）
        List<FeynmanDimensionVO> dims = dimensions();
        Map<String, Integer> idToDim = new HashMap<>();
        for (int d = 0; d < dims.size(); d++) {
            for (FeynmanCriterionVO item : dims.get(d).getItems()) {
                idToDim.put(item.getId(), d);
            }
        }
        Set<String> matchedIds = new HashSet<>();
        if (dto.getCheckedCriterionIds() != null) {
            for (String rawId : dto.getCheckedCriterionIds()) {
                if (rawId == null) {
                    continue;
                }
                String id = rawId.trim();
                if (idToDim.containsKey(id)) {
                    matchedIds.add(id);
                }
            }
        }
        int[] checked = new int[DIM_COUNT];
        for (String id : matchedIds) {
            Integer dim = idToDim.get(id);
            if (dim != null) {
                checked[dim]++;
            }
        }

        // 计分：每维勾选数不超该维总准则数（Set 已去重，此处仅防御性封顶）
        List<FeynmanDimensionResultVO> results = new ArrayList<>();
        int checkedTotal = 0;
        for (int d = 0; d < DIM_COUNT; d++) {
            int hit = Math.min(checked[d], CRITERIA_PER_DIM);
            checkedTotal += hit;
            FeynmanDimensionResultVO r = new FeynmanDimensionResultVO();
            r.setKey(dims.get(d).getKey());
            r.setName(dims.get(d).getName());
            r.setChecked(hit);
            r.setTotal(CRITERIA_PER_DIM);
            r.setScore((int) Math.round(hit * 100.0 / CRITERIA_PER_DIM));
            results.add(r);
        }
        int overall = (int) Math.round(checkedTotal * 100.0 / (CRITERIA_PER_DIM * DIM_COUNT));

        FeynmanSubmitResultVO result = new FeynmanSubmitResultVO();
        result.setTopic(topic);
        result.setOverall(overall);
        result.setDimensions(results);
        result.setComment(commentOf(overall));
        result.setRecorded(Boolean.TRUE);

        // 学习事件落库（统一 record；REQUIRES_NEW，写失败不阻断主流程）
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("topic", topic);
        metadata.put("explanationLength", explanation.length());
        metadata.put("overall", overall);
        metadata.put("checkedTotal", checkedTotal);
        metadata.put("totalCriteria", CRITERIA_PER_DIM * DIM_COUNT);
        for (FeynmanDimensionResultVO r : results) {
            metadata.put(r.getKey().toLowerCase(), r.getScore());
            metadata.put(r.getKey().toLowerCase() + "Checked", r.getChecked());
        }
        if (dto.getDurationSeconds() != null) {
            metadata.put("durationSeconds", dto.getDurationSeconds());
        }
        eventService.record(userId, LearningEventType.FEYNMAN_EXPRESSED, "FEYNMAN", 0L, metadata);
        return result;
    }

    // ==================== rubric 常量（服务端唯一真相源） ====================

    private List<FeynmanDimensionVO> dimensions() {
        List<FeynmanDimensionVO> dims = new ArrayList<>();
        dims.add(dimension("ACCURACY", "准确性",
                criterion("accuracy-1", "逐句核对：没有把概念讲错，或与相近概念混淆"),
                criterion("accuracy-2", "能点出这个知识点最常见的误区 / 易错点，而不只是讲对的部分"),
                criterion("accuracy-3", "引用的机制、数据、术语与所学资料一致，没有凭印象编造"),
                criterion("accuracy-4", "讲完后再对照资料原文确认一遍，意思没有跑偏")));
        dims.add(dimension("CLARITY", "清晰度",
                criterion("clarity-1", "假定读者完全不懂，没有默认读者已知的跳跃"),
                criterion("clarity-2", "用了类比 / 比喻 / 例子，把抽象讲得具体"),
                criterion("clarity-3", "术语首次出现时用一句话解释过"),
                criterion("clarity-4", "句子不绕、无明显口头禅 / 废话堆砌（删掉不影响意思）")));
        dims.add(dimension("COMPLETENESS", "完整度",
                criterion("completeness-1", "回答了「它是什么 / 本质是什么」"),
                criterion("completeness-2", "解释了「它为什么这样工作 / 为什么重要」"),
                criterion("completeness-3", "覆盖了「怎么做 / 应用场景与边界 / 常见坑」"),
                criterion("completeness-4", "结尾向自己提了一个「验证真懂」的问题并作了回答")));
        dims.add(dimension("STRUCTURE", "结构",
                criterion("structure-1", "按逻辑顺序展开（是什么 → 为什么 → 怎么做，或 总分总）"),
                criterion("structure-2", "内容有分段 / 分层，衔接自然不跳"),
                criterion("structure-3", "开头能一句话概括全文（可作为电梯陈述）"),
                criterion("structure-4", "结尾有结论 / 总结，不戛然而止")));
        return dims;
    }

    private FeynmanDimensionVO dimension(String key, String name, FeynmanCriterionVO... items) {
        FeynmanDimensionVO vo = new FeynmanDimensionVO();
        vo.setKey(key);
        vo.setName(name);
        vo.setItems(new ArrayList<>(List.of(items)));
        return vo;
    }

    private FeynmanCriterionVO criterion(String id, String text) {
        FeynmanCriterionVO vo = new FeynmanCriterionVO();
        vo.setId(id);
        vo.setText(text);
        return vo;
    }

    /** 确定性分档点评（总分阈值驱动，非 AI 生成）。 */
    private String commentOf(int overall) {
        if (overall >= 90) {
            return "四维自检接近全绿：这轮表达可以真的拿去「教别人」了。";
        }
        if (overall >= 75) {
            return "整体自检良好：对照未勾选项补一两句，讲给别人前会更稳。";
        }
        if (overall >= 60) {
            return "骨架已成型：按未勾选准则逐条回看正文，优先补完整度与结构。";
        }
        return "表达还不够顺：先回资料再讲一遍——讲不明白，往往是还没学透。";
    }
}
