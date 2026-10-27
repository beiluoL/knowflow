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
import com.knowflow.exam.engine.PaperComposer;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.service.MistakeService;
import com.knowflow.service.QuizService;
import com.knowflow.common.LearningEventType;
import com.knowflow.service.LearningEventService;
import com.knowflow.vo.QuizExplainVO;
import com.knowflow.vo.QuizMistakeVO;
import com.knowflow.vo.QuizPracticeVO;
import com.knowflow.vo.QuizStatsVO;
import com.knowflow.vo.QuizSubmitResultVO;
import com.knowflow.vo.SmartPracticeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** 在线答题业务服务实现：拉题、判分、记录持久化与错题同步。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizServiceImpl extends ServiceImpl<QuizAnswerRecordMapper, QuizAnswerRecord> implements QuizService {

    private final com.knowflow.learning.product.QuizLearningReviewService learningReview;
    private final com.knowflow.mapper.KgEntityMapper knowledgeMapper;
    private final com.knowflow.learning.actions.PersonalLearningResourceScope personal;
    private final com.knowflow.learning.actions.LearningWriteGuard writeGuard;
    private final com.knowflow.learning.evidence.QuizAttemptProvenance attemptProvenance;
    private final com.knowflow.service.ResourceKnowledgeService resourceKnowledge;
    private final QuizQuestionMapper quizQuestionMapper;
    private final MistakeService mistakeService;
    private final LearningMistakeMapper learningMistakeMapper;
    private final ObjectMapper objectMapper;
    private final LearningEventService learningEventService;
    private final com.knowflow.security.DocumentAccessPolicy documentAccess;
    private final com.knowflow.mapper.DocDocumentMapper docMapper;
    private final com.knowflow.mapper.DocCategoryMapper categoryMapper;
    private final com.knowflow.service.AiService aiService;
    private final PaperComposer paperComposer;

    private static final String MISTAKE_SOURCE = "QUIZ";
    private static final String MISTAKE_CATEGORY = "智能题库";

    @Override
    public List<QuizPracticeVO> listPracticeQuestions(Long categoryId, Integer difficulty, String questionType, Integer count) {
        int limit = (count == null || count <= 0) ? 10 : Math.min(count, 50);
        QueryWrapper<QuizQuestion> qw = new QueryWrapper<>();
        Long actor=documentAccess.actor();
        if(!documentAccess.admin(actor)) {
            var categoryIds=categoryMapper.selectList(new LambdaQueryWrapper<com.knowflow.entity.DocCategory>()).stream().filter(c->documentAccess.canReadCategory(c.getId(),actor)).map(com.knowflow.entity.DocCategory::getId).toList();
            var docIds=docMapper.selectList(documentAccess.scope(new LambdaQueryWrapper<com.knowflow.entity.DocDocument>(),actor)).stream().map(com.knowflow.entity.DocDocument::getId).toList();
            qw.and(w->{w.isNull("category_id");if(!categoryIds.isEmpty())w.or().in("category_id",categoryIds);});
            qw.and(w->{w.isNull("doc_id");if(!docIds.isEmpty())w.or().in("doc_id",docIds);});
        }
        qw.eq("status", 1);
        qw.and(w -> w.isNull("review_status").or().eq("review_status",QuizQuestion.REVIEW_APPROVED));
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

    @Override public QuizPracticeVO practiceQuestion(Long id,Long user){
        var q=quizQuestionMapper.selectById(id);
        if(!practicePublished(q))throw new com.knowflow.exception.BusinessException(404,"题目不可用");
        if(!personal.category(q.getCategoryId(),user)||(q.getDocId()!=null&&!personal.document(docMapper.selectById(q.getDocId()),user)))throw new com.knowflow.exception.BusinessException(403,"无权练习此来源题目");
        return toPracticeVO(q);
    }
    /** 与原组卷池兼容：仅历史NULL保持旧合同；明确待审/驳回/未知标记一律不可练习。 */
    private boolean practicePublished(QuizQuestion q) {
        return q != null && Integer.valueOf(1).equals(q.getStatus())
                && (q.getReviewStatus() == null || QuizQuestion.REVIEW_APPROVED.equals(q.getReviewStatus()));
    }

    /** 将题库实体转为练习 VO（隐藏正确答案外的全部字段）。 */
    private QuizPracticeVO toPracticeVO(QuizQuestion q) {
        QuizPracticeVO vo = new QuizPracticeVO();
        vo.setId(q.getId());
        vo.setVersion(q.getVersion());
        vo.setContentHash(com.knowflow.learning.product.QuizAnswerSnapshot.hash(objectMapper,q));
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
        if(dto==null||dto.getAnswers()==null||dto.getAnswers().isEmpty()||dto.getAnswers().stream().anyMatch(a->a==null||a.getQuestionId()==null||a.getQuestionId()<=0||a.getUserAnswer()==null||a.getUserAnswer().length()>50000||a.getTimeCost()!=null&&a.getTimeCost()<0))throw new com.knowflow.exception.BusinessException(400,"请提交有效作答");
        var replay=writeGuard.quiz(userId,dto);if(replay!=null)return replay;
        if(dto!=null&&dto.getAnswers()!=null&&(dto.getAnswers().size()>50||dto.getAnswers().stream().map(QuizSubmitDTO.Item::getQuestionId).distinct().count()!=dto.getAnswers().size()))throw new com.knowflow.exception.BusinessException(400,"一次最多提交50道不同题目");
        writeGuard.lock(userId);
        var locked = quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>().in(QuizQuestion::getId,dto.getAnswers().stream().map(QuizSubmitDTO.Item::getQuestionId).toList()).orderByAsc(QuizQuestion::getId).last("FOR UPDATE")).stream().collect(Collectors.toMap(QuizQuestion::getId,q->q));
        QuizSubmitResultVO result = new QuizSubmitResultVO();
        List<QuizSubmitResultVO.Item> items = new ArrayList<>();
        int correctCount = 0;
        int synced = 0;

        if (dto != null && dto.getAnswers() != null) {
            for (QuizSubmitDTO.Item ans : dto.getAnswers()) {
                if (ans.getQuestionId() == null) {
                    continue;
                }
                QuizQuestion q = locked.get(ans.getQuestionId());
                if(q!=null) {
                    if(q.getDocId()!=null) documentAccess.requireRead(docMapper.selectById(q.getDocId()),userId);
                    if(!documentAccess.canReadCategory(q.getCategoryId(),userId)) throw new com.knowflow.exception.BusinessException(403,"无权访问该题目");
                }
                if (!practicePublished(q)) throw new com.knowflow.exception.BusinessException(404,"题目不存在、未发布或未通过审核");
                if((!Integer.valueOf(1).equals(q.getStatus()) || !java.util.Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","FILL_BLANK").contains(q.getQuestionType())))throw new com.knowflow.exception.BusinessException(400,"该题不支持客观自动判分，请使用专用主观题或安全判题流程");
                if(!personal.category(q.getCategoryId(),userId)||(q.getDocId()!=null&&!personal.document(docMapper.selectById(q.getDocId()),userId)))throw new com.knowflow.exception.BusinessException(403,"无权进行此来源的个人练习");
                String contentHash=com.knowflow.learning.product.QuizAnswerSnapshot.hash(objectMapper,q);
                if(ans.getExpectedContentHash()!=null&&!ans.getExpectedContentHash().equals(contentHash))throw new com.knowflow.exception.BusinessException(409,"题目已变更，请重新加载后作答");
                if(ans.getBaselineAnswerRecordId()!=null&&dto.getRequestId()==null)throw new com.knowflow.exception.BusinessException(400,"复测需要幂等请求编号");
                learningReview.validateBaseline(ans.getBaselineAnswerRecordId(),q.getId(),userId,ans.getExpectedContentHash());
                List<String> options = parseOptions(q.getOptions());
                String userAnswer = ans.getUserAnswer() == null ? "" : ans.getUserAnswer();
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
                record.setQuestionSnapshot(attemptProvenance.stamp(userId,q.getId(),com.knowflow.learning.product.QuizAnswerSnapshot.freeze(objectMapper,q,ans.getBaselineAnswerRecordId(),learningReview.acceptedKnowledge("QUIZ",q.getId(),userId))));
                if(!this.save(record))throw new IllegalStateException("Answer persistence failed");
                // Phase 1：一次答题 = 一条核心事件 QUESTION_ANSWERED（行为+结果合一，避免 ANSWERED+CORRECT/WRONG 双事件）
                var eventMeta=new java.util.LinkedHashMap<String,Object>();eventMeta.put("correct",correct);eventMeta.put("score",score);eventMeta.put("timeCost",record.getTimeCost());eventMeta.put("answerRecordId",record.getId());if(dto.getRequestId()!=null)eventMeta.put("requestId",dto.getRequestId());
                eventMeta.put("questionContentHash",contentHash);eventMeta.put("gradingRule",com.knowflow.learning.product.QuizAnswerSnapshot.RULE);if(q.getVersion()!=null)eventMeta.put("questionVersion",q.getVersion());if(ans.getBaselineAnswerRecordId()!=null)eventMeta.put("baselineAnswerRecordId",ans.getBaselineAnswerRecordId());
                var mapped=resourceKnowledge.resolveKnowledgeIds("QUIZ",q.getId());
                mapped.stream().filter(id->{var k=knowledgeMapper.selectById(id);return k!=null&&personal.category(k.getCategoryId(),userId)&&(k.getDocId()==null||personal.document(docMapper.selectById(k.getDocId()),userId));}).findFirst().ifPresent(id->eventMeta.put("knowledgeId",id));
                learningEventService.recordRequired(userId, LearningEventType.QUESTION_ANSWERED.name(), "QUIZ", q.getId(),eventMeta);

                String correctDisplay = toDisplayAnswer(q.getQuestionType(), q.getAnswer(), options);

                // 答错自动同步错题本（幂等：同用户 + 同题干；Phase 0 起记录原题ID，打通 错题→原题→解析→再练习）
                if (!correct) {
                    LearningMistake mistake = new LearningMistake();
                    mistake.setQuestion(buildMistakeQuestion(q));
                    mistake.setWrongAnswer(toDisplayAnswer(q.getQuestionType(), userAnswer, options));
                    mistake.setCorrectAnswer(correctDisplay);
                    mistake.setCategory(MISTAKE_CATEGORY);
                    mistake.setDifficulty(q.getDifficulty());
                    mistake.setSource(MISTAKE_SOURCE);
                    mistake.setQuestionId(q.getId());
                    mistakeService.addMistake(mistake, userId);
                    synced++;
                } else {
                    correctCount++;
                    learningMistakeMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LearningMistake>().eq(LearningMistake::getUserId,userId).eq(LearningMistake::getQuestionId,q.getId()).eq(LearningMistake::getSource,MISTAKE_SOURCE).set(LearningMistake::getMastered,1).set(LearningMistake::getLastReviewTime,java.time.LocalDateTime.now()));
                }

                QuizSubmitResultVO.Item item = new QuizSubmitResultVO.Item();
                item.setQuestionId(q.getId());
                item.setAnswerRecordId(record.getId());item.setQuestionVersion(q.getVersion());item.setContentHash(contentHash);
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
        writeGuard.rememberQuiz(userId,dto,result);
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
            throw new com.knowflow.exception.BusinessException(404, "错题记录不存在");
        }
        mistake.setReviewCount(mistake.getReviewCount() != null ? mistake.getReviewCount() + 1 : 1);
        mistake.setUpdateTime(java.time.LocalDateTime.now());
        if (Boolean.TRUE.equals(isCorrect)) {
            mistake.setMastered(1);
        }
        learningMistakeMapper.updateById(mistake);
        return mistake.getMastered() == 1;
    }

    // ==================== Phase 3H：题目讲解（AI 可选，降级到已有解析） ====================

    @Override
    public QuizExplainVO explainQuestion(Long questionId, String userAnswer, Long userId) {
        QuizQuestion q = quizQuestionMapper.selectById(questionId);
        if (!practicePublished(q)) {
            throw new com.knowflow.exception.BusinessException(404, "题目不存在或未发布");
        }
        if (QuizQuestion.TYPE_CODE.equals(q.getQuestionType())) {
            throw new com.knowflow.exception.BusinessException(400,
                    "算法题请使用算法练习页的「AI 点评 / 分级提示」功能");
        }
        // 与拉题同一道访问门禁：匿名/私有分类与私有文档题目不可读
        Long actor = documentAccess.actor();
        if (!documentAccess.admin(actor)) {
            if (q.getCategoryId() != null && !documentAccess.canReadCategory(q.getCategoryId(), actor)) {
                throw new com.knowflow.exception.BusinessException(403, "无权查看该题目");
            }
            if (q.getDocId() != null) {
                com.knowflow.entity.DocDocument doc = docMapper.selectById(q.getDocId());
                if (doc == null || !documentAccess.canRead(doc, actor)) {
                    throw new com.knowflow.exception.BusinessException(403, "无权查看该题目");
                }
            }
        }

        QuizExplainVO vo = new QuizExplainVO();
        vo.setQuestionId(q.getId());
        vo.setAnswer(q.getAnswer());
        vo.setTopicCode(q.getTopicCode());
        vo.setQuestionType(q.getQuestionType());
        boolean hasExplanation = q.getExplanation() != null && !q.getExplanation().isBlank();
        vo.setHasExplanation(hasExplanation);
        vo.setExplanation(hasExplanation ? q.getExplanation() : null);

        if (aiService.isConfigured(userId)) {
            try {
                String systemPrompt = "你是一名耐心的编程题库讲师。请针对用户的作答给出个性化讲解："
                        + "先判断作答是否正确，再讲清核心考点、错因（如有）与正确思路，"
                        + "最后给出一个避免再错的记忆点或延伸练习建议。用中文 Markdown 回答，不要编造题目以外的信息。";
                StringBuilder userPrompt = new StringBuilder();
                userPrompt.append("题型：").append(q.getQuestionType()).append("\n");
                userPrompt.append("题目：").append(q.getTitle()).append("\n").append(q.getContent()).append("\n");
                if (q.getOptions() != null && !q.getOptions().isBlank()) {
                    userPrompt.append("选项：").append(q.getOptions()).append("\n");
                }
                userPrompt.append("标准答案：").append(q.getAnswer()).append("\n");
                if (hasExplanation) {
                    userPrompt.append("已有解析：").append(q.getExplanation()).append("\n");
                }
                userPrompt.append("用户作答：")
                        .append(userAnswer == null || userAnswer.isBlank() ? "（未作答，仅请求讲解）" : userAnswer);
                String out = aiService.complete(systemPrompt, userPrompt.toString(), null, userId);
                if (out != null && !out.isBlank()) {
                    vo.setMode("AI");
                    vo.setContent(out.trim());
                    return vo;
                }
                vo.setFailureKind("AI_INVALID_RESPONSE");
            } catch (com.knowflow.ai.capability.AiCapabilityException e) {
                log.warn("题目讲解降级 questionId={} kind={}", questionId, e.getKind());
                vo.setFailureKind("AI_" + e.getKind().name());
            } catch (Exception e) {
                log.warn("题目讲解降级 questionId={}: {}", questionId, e.getMessage());
                vo.setFailureKind("AI_UNKNOWN");
            }
        } else {
            vo.setFailureKind("AI_UNCONFIGURED");
        }

        // 基础模式：只展示题库已有答案与解析；没有解析时显式“暂无解析”，不编造
        vo.setMode("BASIC");
        vo.setContent(hasExplanation ? q.getExplanation() : null);
        return vo;
    }

    // ==================== Phase 3H：确定性智能练习推荐（REVIEW 规则引擎） ====================

    @Override
    public SmartPracticeVO recommendPractice(Long userId, List<String> topics, List<Integer> difficulties,
                                             Integer count, Long seed) {
        int n = (count == null || count <= 0) ? 10 : Math.min(count, 20);
        ExamRequests.ComposeRequest req = new ExamRequests.ComposeRequest();
        req.setStrategy(PaperComposer.STRATEGY_REVIEW);
        req.setTopics(topics == null ? null : new ArrayList<>(topics));
        req.setDifficulties(difficulties);
        req.setQuestionCount(n);
        // 智能练习只选客观题（自动判分口径干净）；权重 4:2:2:2
        req.setTypeDistribution(scaledObjectiveDistribution(n));
        req.setTotalScore(100);
        req.setDurationMinutes(Math.max(5, n * 2));
        req.setSeed(seed);
        // 保留优先级顺序：到期/高频错题排在前面，不做题目乱序
        req.setShuffleQuestion(0);

        PaperComposer.SelectionResult sr = paperComposer.compose(userId, req);

        SmartPracticeVO vo = new SmartPracticeVO();
        vo.setRuleVersion(sr.ruleVersion);
        vo.setBankVersion(sr.bankVersion);
        vo.setSufficient(sr.sufficient);
        vo.setMessage(sr.message);
        vo.setReasonCounts(sr.reasonCounts);
        vo.setItems(sr.selected.stream().map(sq -> {
            QuizQuestion q = sq.question;
            SmartPracticeVO.Item item = new SmartPracticeVO.Item();
            item.setId(q.getId());
            item.setTitle(q.getTitle());
            item.setContent(q.getContent());
            item.setQuestionType(q.getQuestionType());
            item.setOptions(parseOptions(q.getOptions()));
            item.setAnswer(q.getAnswer());
            item.setExplanation(q.getExplanation());
            item.setDifficulty(q.getDifficulty());
            item.setTags(q.getTags());
            item.setTopicCode(q.getTopicCode());
            item.setReason(sq.reason);
            return item;
        }).collect(Collectors.toList()));
        return vo;
    }

    /** 客观题分布：单选 40% / 判断 20% / 多选 20% / 填空 20%，最大余数法缩放到 n。 */
    private Map<String, Integer> scaledObjectiveDistribution(int n) {
        Map<String, Integer> weights = new java.util.LinkedHashMap<>();
        weights.put(QuizQuestion.TYPE_SINGLE_CHOICE, 4);
        weights.put(QuizQuestion.TYPE_TRUE_FALSE, 2);
        weights.put(QuizQuestion.TYPE_MULTIPLE_CHOICE, 2);
        weights.put(QuizQuestion.TYPE_FILL_BLANK, 2);
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        Map<String, Double> frac = new HashMap<>();
        int assigned = 0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            double exact = n * e.getValue() / 10.0;
            int floor = (int) Math.floor(exact);
            out.put(e.getKey(), floor);
            frac.put(e.getKey(), exact - floor);
            assigned += floor;
        }
        int remaining = n - assigned;
        List<String> order = new ArrayList<>(weights.keySet());
        order.sort((a, b) -> Double.compare(frac.get(b), frac.get(a)));
        int idx = 0;
        while (remaining > 0) {
            out.merge(order.get(idx % order.size()), 1, Integer::sum);
            remaining--;
            idx++;
        }
        out.values().removeIf(v -> v <= 0);
        return out;
    }
}
