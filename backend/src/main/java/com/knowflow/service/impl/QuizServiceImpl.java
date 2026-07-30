package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.QuizSubmitDTO;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizAnswerRecord;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.service.MistakeService;
import com.knowflow.service.QuizService;
import com.knowflow.vo.QuizMistakeVO;
import com.knowflow.vo.QuizPracticeVO;
import com.knowflow.vo.QuizStatsVO;
import com.knowflow.vo.QuizSubmitResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** 在线答题业务服务实现：拉题、判分、记录持久化与错题同步。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizServiceImpl extends ServiceImpl<QuizAnswerRecordMapper, QuizAnswerRecord> implements QuizService {

    private final QuizQuestionMapper quizQuestionMapper;
    private final MistakeService mistakeService;
    private final LearningMistakeMapper learningMistakeMapper;
    private final ObjectMapper objectMapper;

    private static final String MISTAKE_SOURCE = "QUIZ";
    private static final String MISTAKE_CATEGORY = "智能题库";

    @Override
    public List<QuizPracticeVO> listPracticeQuestions(Long categoryId, Integer difficulty, String questionType, Integer count) {
        int limit = (count == null || count <= 0) ? 10 : Math.min(count, 50);
        QueryWrapper<QuizQuestion> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        if (categoryId != null) {
            qw.eq("category_id", categoryId);
        }
        if (difficulty != null && difficulty > 0) {
            qw.eq("difficulty", difficulty);
        }
        if (questionType != null && !questionType.isBlank()) {
            qw.eq("question_type", questionType);
        }
        // H2 与 MySQL 均支持 RAND()，随机取题避免每次同序
        qw.last("ORDER BY RAND() LIMIT " + limit);
        List<QuizQuestion> questions = quizQuestionMapper.selectList(qw);
        return questions.stream().map(this::toPracticeVO).collect(Collectors.toList());
    }

    /** 将题库实体转为练习 VO（隐藏正确答案外的全部字段）。 */
    private QuizPracticeVO toPracticeVO(QuizQuestion q) {
        QuizPracticeVO vo = new QuizPracticeVO();
        vo.setId(q.getId());
        vo.setTitle(q.getTitle());
        vo.setContent(q.getContent());
        vo.setQuestionType(q.getQuestionType());
        vo.setOptions(parseOptions(q.getOptions()));
        vo.setAnswer(q.getAnswer());
        vo.setExplanation(q.getExplanation());
        vo.setDifficulty(q.getDifficulty());
        vo.setTags(q.getTags());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuizSubmitResultVO submit(QuizSubmitDTO dto, Long userId) {
        QuizSubmitResultVO result = new QuizSubmitResultVO();
        List<QuizSubmitResultVO.Item> items = new ArrayList<>();
        int correctCount = 0;
        int synced = 0;

        if (dto != null && dto.getAnswers() != null) {
            for (QuizSubmitDTO.Item ans : dto.getAnswers()) {
                if (ans.getQuestionId() == null) {
                    continue;
                }
                QuizQuestion q = quizQuestionMapper.selectById(ans.getQuestionId());
                if (q == null) {
                    continue;
                }
                List<String> options = parseOptions(q.getOptions());
                String userAnswer = ans.getUserAnswer() == null ? "" : ans.getUserAnswer().trim();
                boolean correct = judge(q.getQuestionType(), q.getAnswer(), userAnswer);
                int score = correct ? 100 : 0;

                // 持久化答题记录
                QuizAnswerRecord record = new QuizAnswerRecord();
                record.setUserId(userId);
                record.setQuestionId(q.getId());
                record.setUserAnswer(userAnswer);
                record.setIsCorrect(correct ? 1 : 0);
                record.setScore(score);
                record.setTimeCost(ans.getTimeCost() == null ? 0 : ans.getTimeCost());
                this.save(record);

                String correctDisplay = toDisplayAnswer(q.getQuestionType(), q.getAnswer(), options);

                // 答错自动同步错题本（幂等：同用户 + 同题干）
                if (!correct) {
                    LearningMistake mistake = new LearningMistake();
                    mistake.setQuestion(buildMistakeQuestion(q));
                    mistake.setWrongAnswer(toDisplayAnswer(q.getQuestionType(), userAnswer, options));
                    mistake.setCorrectAnswer(correctDisplay);
                    mistake.setCategory(MISTAKE_CATEGORY);
                    mistake.setDifficulty(q.getDifficulty());
                    mistake.setSource(MISTAKE_SOURCE);
                    try {
                        mistakeService.addMistake(mistake, userId);
                        synced++;
                    } catch (Exception e) {
                        log.warn("同步错题本失败 questionId={}: {}", q.getId(), e.getMessage());
                    }
                } else {
                    correctCount++;
                }

                QuizSubmitResultVO.Item item = new QuizSubmitResultVO.Item();
                item.setQuestionId(q.getId());
                item.setUserAnswer(userAnswer);
                item.setCorrectAnswer(correctDisplay);
                item.setCorrect(correct);
                item.setExplanation(q.getExplanation());
                item.setScore(score);
                items.add(item);
            }
        }

        int total = items.size();
        result.setTotal(total);
        result.setCorrect(correctCount);
        result.setWrong(total - correctCount);
        result.setAccuracy(total == 0 ? 0 : Math.round(correctCount * 100f / total));
        result.setSyncedMistakes(synced);
        result.setItems(items);
        return result;
    }

    @Override
    public QuizStatsVO getStats(Long userId) {
        long total = this.count(new LambdaQueryWrapper<QuizAnswerRecord>()
                .eq(QuizAnswerRecord::getUserId, userId));
        long correct = this.count(new LambdaQueryWrapper<QuizAnswerRecord>()
                .eq(QuizAnswerRecord::getUserId, userId)
                .eq(QuizAnswerRecord::getIsCorrect, 1));
        QuizStatsVO vo = new QuizStatsVO();
        vo.setTotal((int) total);
        vo.setCorrect((int) correct);
        vo.setWrong((int) (total - correct));
        vo.setAccuracy(total == 0 ? 0 : Math.round(correct * 100f / total));
        return vo;
    }

    // ==================== 判分与格式化 ====================

    /** 自动判分。单选/判断精确匹配，多选按索引集合比较，填空忽略大小写，简答做宽松包含匹配。 */
    private boolean judge(String type, String answer, String userAnswer) {
        if (answer == null) {
            return false;
        }
        String std = answer.trim();
        String usr = userAnswer == null ? "" : userAnswer.trim();
        if (usr.isEmpty()) {
            return false;
        }
        return switch (type == null ? "" : type) {
            case QuizQuestion.TYPE_MULTIPLE_CHOICE -> normalizeIndexSet(std).equals(normalizeIndexSet(usr));
            case QuizQuestion.TYPE_TRUE_FALSE, QuizQuestion.TYPE_SINGLE_CHOICE -> std.equalsIgnoreCase(usr);
            case QuizQuestion.TYPE_FILL_BLANK -> std.equalsIgnoreCase(usr);
            case QuizQuestion.TYPE_SHORT_ANSWER -> {
                // 主观题无法机器精确判分：做宽松包含匹配，任一方向命中即视为答对
                String a = std.toLowerCase();
                String b = usr.toLowerCase();
                yield a.contains(b) || b.contains(a);
            }
            default -> std.equalsIgnoreCase(usr);
        };
    }

    /** 将逗号分隔的选项索引归一化为有序集合，用于多选题比较（忽略顺序与空白）。 */
    private TreeSet<String> normalizeIndexSet(String s) {
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 将答案转为展示文本：选择题索引转为选项内容，判断题转为「正确/错误」，其余原样返回。 */
    private String toDisplayAnswer(String type, String answer, List<String> options) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String a = answer.trim();
        return switch (type == null ? "" : type) {
            case QuizQuestion.TYPE_SINGLE_CHOICE -> optionText(options, a);
            case QuizQuestion.TYPE_MULTIPLE_CHOICE -> Arrays.stream(a.split(","))
                    .map(String::trim)
                    .filter(x -> !x.isEmpty())
                    .map(x -> optionText(options, x))
                    .collect(Collectors.joining("；"));
            case QuizQuestion.TYPE_TRUE_FALSE -> "true".equalsIgnoreCase(a) ? "正确" : "错误";
            default -> a;
        };
    }

    /** 根据索引取选项内容，形如「A. 内容」；索引非法时回退为原始文本。 */
    private String optionText(List<String> options, String idxStr) {
        try {
            int idx = Integer.parseInt(idxStr.trim());
            if (options != null && idx >= 0 && idx < options.size()) {
                return (char) ('A' + idx) + ". " + options.get(idx);
            }
        } catch (NumberFormatException ignored) {
            // 非索引答案（异常数据），直接返回原文
        }
        return idxStr;
    }

    /** 错题题干：优先「标题：题干」组合，便于错题本可读。 */
    private String buildMistakeQuestion(QuizQuestion q) {
        String content = q.getContent() == null ? "" : q.getContent().trim();
        String title = q.getTitle() == null ? "" : q.getTitle().trim();
        if (!title.isEmpty() && !content.isEmpty() && !content.startsWith(title)) {
            return title + "：" + content;
        }
        return content.isEmpty() ? title : content;
    }

    /** 解析 options JSON 数组为字符串列表，异常或空时返回空列表。 */
    private List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Object parsed = objectMapper.readValue(optionsJson, new TypeReference<List<Object>>() {});
            List<?> raw = (List<?>) parsed;
            List<String> result = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    // 兼容 [{"label":"A","content":"xxx"}] 结构
                    Object v = m.containsKey("content") ? m.get("content") : m.get("text");
                    result.add(v == null ? String.valueOf(m) : String.valueOf(v));
                } else {
                    result.add(String.valueOf(o));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析题目选项失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<QuizMistakeVO> listMistakePractice(Long userId, Integer count) {
        int limit = (count == null || count <= 0) ? 10 : Math.min(count, 50);
        List<LearningMistake> mistakes = learningMistakeMapper.selectList(
                new LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, userId)
                        .eq(LearningMistake::getMastered, 0)
                        .orderByAsc(LearningMistake::getUpdateTime)
                        .last("LIMIT " + limit));
        List<QuizMistakeVO> result = new ArrayList<>();
        for (LearningMistake m : mistakes) {
            QuizMistakeVO vo = new QuizMistakeVO();
            vo.setId(m.getId());
            vo.setQuestion(m.getQuestion());
            vo.setWrongAnswer(m.getWrongAnswer());
            vo.setCorrectAnswer(m.getCorrectAnswer());
            vo.setCategory(m.getCategory());
            vo.setDifficulty(m.getDifficulty());
            vo.setReviewCount(m.getReviewCount() != null ? m.getReviewCount() : 0);
            vo.setMastered(false);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewMistake(Long mistakeId, Boolean isCorrect, Long userId) {
        LearningMistake mistake = learningMistakeMapper.selectById(mistakeId);
        if (mistake == null || !mistake.getUserId().equals(userId)) {
            throw new com.knowflow.exception.BusinessException("错题记录不存在");
        }
        mistake.setReviewCount(mistake.getReviewCount() != null ? mistake.getReviewCount() + 1 : 1);
        mistake.setUpdateTime(java.time.LocalDateTime.now());
        if (Boolean.TRUE.equals(isCorrect)) {
            mistake.setMastered(1);
        }
        learningMistakeMapper.updateById(mistake);
        return mistake.getMastered() == 1;
    }
}
