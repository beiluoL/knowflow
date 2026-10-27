package com.knowflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.knowflow.common.LearningEventType;
import com.knowflow.dto.FirstPrinciplesSubmitDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.LearningEventService;
import com.knowflow.service.LearningFirstPrinciplesService;
import com.knowflow.vo.FirstPrinciplesRubricVO;
import com.knowflow.vo.FirstPrinciplesStepVO;
import com.knowflow.vo.FirstPrinciplesSubmitResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 第一性原理推导实现（Architecture-4.1-§14~17）。
 * <p>
 * 口径（Deterministic-first，§16/§33）：只评估「结构完整度」——
 * 六个环节各自去空白后是否达到最短字数（8），完整度 = 合格环节数 / 6 × 100。
 * <b>系统不验证语义正确性</b>：没有可靠的机器参照系，因此绝不声称「已理解你的水平」。
 * 未来若接入 LLM，也只能作为评价辅助，不得直接修改 Mastery / Diagnosis / UserApp（§34）。
 * <p>
 * 事件：FIRST_PRINCIPLES_DERIVED（resourceType=FIRST_PRINCIPLES，resourceId=0：无内容实体），
 * 元数据携带 topic/completeness/filled/total/missing，不直写掌握度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningFirstPrinciplesServiceImpl implements LearningFirstPrinciplesService {

    /** 环节 key（顺序固定，与前端 FIRST_PRINCIPLES_STEPS 严格一致） */
    private static final List<String> STEP_KEYS = Arrays.asList(
            "facts", "assumptions", "constraints", "mechanism", "causality", "derivation");

    /** 单环节最短有效字数（去空白后） */
    private static final int MIN_STEP_LENGTH = 8;
    /** 主题最短长度 */
    private static final int MIN_TOPIC_LENGTH = 2;
    /** 主题最长长度 */
    private static final int MAX_TOPIC_LENGTH = 200;
    /** 单环节最长长度 */
    private static final int MAX_STEP_LENGTH = 4000;

    private final LearningEventService eventService;

    @Override
    public FirstPrinciplesRubricVO rubric() {
        FirstPrinciplesRubricVO vo = new FirstPrinciplesRubricVO();
        vo.setSteps(steps());
        vo.setScoringNote("结构完整度 = 填写合格的环节数 / 6。"
                + "这只衡量「你把推导过程写全了没有」，不衡量「你推得对不对」——"
                + "系统没有可靠的语义参照系，不会替你判断正确性。"
                + "分数作为结构化理解证据入库，不直写掌握度。");
        return vo;
    }

    @Override
    public FirstPrinciplesSubmitResultVO submit(Long userId, FirstPrinciplesSubmitDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String topic = StrUtil.trimToEmpty(dto.getTopic());
        if (topic.length() < MIN_TOPIC_LENGTH) {
            throw new BusinessException(400, "请填写推导主题");
        }
        if (topic.length() > MAX_TOPIC_LENGTH) {
            throw new BusinessException(400, "推导主题过长（上限 " + MAX_TOPIC_LENGTH + " 字符）");
        }
        Map<String, String> answers = new HashMap<>();
        answers.put("facts", StrUtil.trimToEmpty(dto.getFacts()));
        answers.put("assumptions", StrUtil.trimToEmpty(dto.getAssumptions()));
        answers.put("constraints", StrUtil.trimToEmpty(dto.getConstraints()));
        answers.put("mechanism", StrUtil.trimToEmpty(dto.getMechanism()));
        answers.put("causality", StrUtil.trimToEmpty(dto.getCausality()));
        answers.put("derivation", StrUtil.trimToEmpty(dto.getDerivation()));

        List<FirstPrinciplesStepVO> defs = steps();
        List<String> missing = new ArrayList<>();
        List<FirstPrinciplesStepVO> stepResults = new ArrayList<>();
        int filled = 0;
        for (FirstPrinciplesStepVO def : defs) {
            String text = answers.getOrDefault(def.getKey(), "");
            if (text.length() > MAX_STEP_LENGTH) {
                throw new BusinessException(400, "「" + def.getTitle() + "」内容过长（上限 " + MAX_STEP_LENGTH + " 字符）");
            }
            boolean ok = text.replaceAll("\\s+", "").length() >= MIN_STEP_LENGTH;
            if (ok) {
                filled++;
            } else {
                missing.add(def.getKey());
            }
            FirstPrinciplesStepVO r = new FirstPrinciplesStepVO();
            r.setKey(def.getKey());
            r.setIndex(def.getIndex());
            r.setTitle(def.getTitle());
            r.setHint(def.getHint());
            r.setPlaceholder(def.getPlaceholder());
            r.setMinLength(MIN_STEP_LENGTH);
            stepResults.add(r);
        }

        int total = STEP_KEYS.size();
        int completeness = (int) Math.round(filled * 100.0 / total);

        FirstPrinciplesSubmitResultVO result = new FirstPrinciplesSubmitResultVO();
        result.setTopic(topic);
        result.setCompleteness(completeness);
        result.setFilled(filled);
        result.setTotal(total);
        result.setMissing(missing);
        result.setSteps(stepResults);
        result.setComment(commentOf(completeness));
        result.setRecorded(Boolean.TRUE);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("topic", topic);
        metadata.put("completeness", completeness);
        metadata.put("filled", filled);
        metadata.put("total", total);
        metadata.put("missing", missing);
        if (dto.getDurationSeconds() != null) {
            metadata.put("durationSeconds", dto.getDurationSeconds());
        }
        // Architecture-4.2 §11：页面透传的知识点上下文（真实来源），供 Evidence 绑定到具体知识点
        if (dto.getKnowledgeId() != null) {
            metadata.put("knowledgeId", dto.getKnowledgeId());
        }
        eventService.record(userId, LearningEventType.FIRST_PRINCIPLES_DERIVED, "FIRST_PRINCIPLES", 0L, metadata);
        return result;
    }

    /** 确定性分档点评（阈值驱动，非 AI 生成；如实只谈结构） */
    private String commentOf(int completeness) {
        if (completeness >= 100) {
            return "六个环节都写全了。下一步是对照资料检查「底层机制」和「因果链」是否站得住。";
        }
        if (completeness >= 67) {
            return "主体结构已成型，补全缺失环节后这套推导才算完整。";
        }
        if (completeness >= 34) {
            return "已经有基础框架，但关键环节还空着，推导链是断的。";
        }
        return "推导还很不完整。第一性原理的价值在于过程，建议从「已知事实」重新拆。";
    }

    /** 六环节常量定义（服务端唯一真相源） */
    private List<FirstPrinciplesStepVO> steps() {
        List<FirstPrinciplesStepVO> list = new ArrayList<>();
        list.add(step("facts", 1, "已知事实",
                "只写可以验证的事实，不写类比、不写结论",
                "例：对象创建后可能被回收；堆内存大小有限"));
        list.add(step("assumptions", 2, "假设",
                "你默认成立、但没有亲自验证过的东西",
                "例：大多数对象朝生夕死（需要验证，不是事实）"));
        list.add(step("constraints", 3, "约束",
                "绕不过去的硬边界：物理限制、资源上限、规则要求",
                "例：回收时必须停线程或处理并发；内存不能无限增长"));
        list.add(step("mechanism", 4, "底层机制",
                "拆到不能再拆的基本原理，而不是「因为框架这么做的」",
                "例：可达性分析 → 标记存活对象 → 回收不可达对象 → 整理/复制"));
        list.add(step("causality", 5, "因果关系",
                "机制之间如何一步步推出结果，写出因果链",
                "例：因为对象朝生夕死 → 所以分代 → 所以年轻代用复制算法成本最低"));
        list.add(step("derivation", 6, "重新推导",
                "忘掉结论，只从上面 1-5 重新推出原来的结论",
                "例：从「可达性 + 分代假设 + 停顿约束」重新推出「需要分代收集器」"));
        return list;
    }

    private FirstPrinciplesStepVO step(String key, int index, String title, String hint, String placeholder) {
        FirstPrinciplesStepVO vo = new FirstPrinciplesStepVO();
        vo.setKey(key);
        vo.setIndex(index);
        vo.setTitle(title);
        vo.setHint(hint);
        vo.setPlaceholder(placeholder);
        vo.setMinLength(MIN_STEP_LENGTH);
        return vo;
    }
}
