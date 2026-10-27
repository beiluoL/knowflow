package com.knowflow.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.PageResult;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.core.InterviewQuestionBank;
import com.knowflow.interview.core.InterviewQuestionBank.SeedQuestion;
import com.knowflow.interview.core.InterviewQuestionBank.Track;
import com.knowflow.interview.entity.MockInterviewSession;
import com.knowflow.interview.mapper.MockInterviewSessionMapper;
import com.knowflow.interview.service.MockInterviewService;
import com.knowflow.interview.vo.InterviewVos.AnswerRequest;
import com.knowflow.interview.vo.InterviewVos.AnswerVO;
import com.knowflow.interview.vo.InterviewVos.FollowupAnswer;
import com.knowflow.interview.vo.InterviewVos.FollowupAnswerVO;
import com.knowflow.interview.vo.InterviewVos.QuestionVO;
import com.knowflow.interview.vo.InterviewVos.RubricVO;
import com.knowflow.interview.vo.InterviewVos.SessionSummaryVO;
import com.knowflow.interview.vo.InterviewVos.SessionVO;
import com.knowflow.interview.vo.InterviewVos.StartRequest;
import com.knowflow.interview.vo.InterviewVos.TrackVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模拟面试基础模式实现（无 AI 依赖）。
 *
 * <p>题目在开始时整体快照；作答以题目 id 为键幂等覆盖保存；结束由服务端统一算均分与用时。
 */
@Service
@RequiredArgsConstructor
public class MockInterviewServiceImpl implements MockInterviewService {

    private static final int DEFAULT_QUESTION_COUNT = 3;
    private static final int MAX_QUESTION_COUNT = 5;
    private static final int MAX_ANSWER_CHARS = 8000;
    private static final int MAX_FOLLOWUPS = 5;
    private static final int MAX_FOLLOWUP_CHARS = 2000;
    private static final int MAX_COMMENT_CHARS = 1000;

    private final MockInterviewSessionMapper sessionMapper;
    private final com.knowflow.interview.v2.InterviewV2Guard v2Guard;
    private final ObjectMapper objectMapper;
    private final com.knowflow.interview.core.ContentStudioInterviewBank contentBank;
    private final com.knowflow.interview.radar.RadarInterviewBank radarBank;

    @Override
    public List<TrackVO> listTracks() {
        return availableTracks(com.knowflow.common.SecurityUtils.getCurrentUserId()).stream().filter(t->!t.code().startsWith("RADAR_"))
                .map(t -> {
                    TrackVO vo = new TrackVO();
                    vo.setCode(t.code());
                    vo.setName(t.name());
                    vo.setDescription(t.description());
                    vo.setQuestionPoolSize(t.questions().size());
                    return vo;
                }).toList();
    }

    @Override
    public List<Map<String,String>> listQuestions(Long actor,String track){
        return findTrack(track,actor).questions().stream().map(q->Map.of("id",q.id(),"title",q.title())).toList();
    }

    @Override
    @Transactional
    public SessionVO start(Long userId, StartRequest req) {
        if (req == null || req.getTrack() == null || req.getTrack().isBlank()) {
            throw new BusinessException(400, "请选择面试方向");
        }
        Track track = findTrack(req.getTrack(),userId);
        int count = normalizeCount(req.getQuestionCount(), track.questions().size());

        List<SeedQuestion> picked;
        if(req.getQuestionId()!=null&&!req.getQuestionId().isBlank()){
            SeedQuestion selected=track.questions().stream().filter(q->q.id().equals(req.getQuestionId())).findFirst()
                    .orElseThrow(()->new BusinessException(400,"所选场景不属于当前可访问面试方向"));
            picked=List.of(selected);count=1;
        }else{
            picked=new ArrayList<>(track.questions());Collections.shuffle(picked,new Random());picked=picked.subList(0,count);
        }

        MockInterviewSession session = new MockInterviewSession();
        session.setUserId(userId);
        session.setTrack(track.code());
        session.setMode(track.code().startsWith("RADAR_")?"STRICT":MockInterviewSession.MODE_BASIC);
        session.setStatus(MockInterviewSession.STATUS_IN_PROGRESS);
        session.setQuestionCount(count);
        session.setQuestionsJson(writeJson(picked));
        session.setAnswersJson(null);
        session.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return toVO(session, picked, new ArrayList<>());
    }

    @Override
    @Transactional(readOnly = true)
    public SessionVO getOwn(Long userId, Long sessionId) {
        MockInterviewSession session = requireOwnSession(userId, sessionId);
        return toVO(session);
    }

    @Override
    @Transactional
    public SessionVO saveAnswer(Long userId, Long sessionId, AnswerRequest req) {
        MockInterviewSession session = requireOwnSession(userId, sessionId, true);
        if (!MockInterviewSession.MODE_BASIC.equals(session.getMode()) || v2Guard.isV2(sessionId)) {
            throw new BusinessException(409, "V2会话请通过受控面试入口操作");
        }
        if (!MockInterviewSession.STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new BusinessException(400, "面试已结束，不能再修改作答");
        }
        List<SeedQuestion> questions = readQuestions(session);
        SeedQuestion target = questions.stream()
                .filter(q -> Objects.equals(q.id(), req == null ? null : req.getQuestionId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "题目不属于本次面试"));

        String answer = trimmed(req.getAnswer(), MAX_ANSWER_CHARS, "作答内容");
        Integer selfScore = req.getSelfScore();
        if (selfScore == null || selfScore < 1 || selfScore > 5) {
            throw new BusinessException(400, "请按 1-5 分量表完成自评");
        }
        List<FollowupAnswer> followups = normalizeFollowups(req.getFollowups(), target);
        String comment = optionalTrimmed(req.getSelfComment(), MAX_COMMENT_CHARS, "自评备注");

        List<AnswerRequest> answers = readAnswers(session);
        answers.removeIf(a -> Objects.equals(a.getQuestionId(), target.id()));
        AnswerRequest stored = new AnswerRequest();
        stored.setQuestionId(target.id());
        stored.setAnswer(answer);
        stored.setFollowups(followups);
        stored.setSelfScore(selfScore);
        stored.setSelfComment(comment);
        answers.add(stored);

        session.setAnswersJson(writeJson(answers));
        sessionMapper.updateById(session);
        return toVO(session);
    }

    @Override
    @Transactional
    public SessionVO finish(Long userId, Long sessionId) {
        MockInterviewSession session = requireOwnSession(userId, sessionId, true);
        if (!MockInterviewSession.MODE_BASIC.equals(session.getMode()) || v2Guard.isV2(sessionId)) {
            throw new BusinessException(409, "V2会话请通过受控面试入口操作");
        }
        if (!MockInterviewSession.STATUS_IN_PROGRESS.equals(session.getStatus())) {
            return toVO(session);
        }
        List<SeedQuestion> questions = readQuestions(session);
        List<AnswerRequest> answers = readAnswers(session);
        Set<String> answeredIds = answers.stream()
                .map(AnswerRequest::getQuestionId).collect(Collectors.toSet());
        List<String> missing = questions.stream().map(SeedQuestion::id)
                .filter(id -> !answeredIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(400, "还有 " + missing.size() + " 道题未作答并自评，完成后才能结束面试");
        }
        double avg = answers.stream().mapToInt(AnswerRequest::getSelfScore).average().orElse(0);
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(MockInterviewSession.STATUS_FINISHED);
        session.setAvgSelfScore(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
        session.setFinishedAt(now);
        LocalDateTime started = session.getStartedAt() == null ? now : session.getStartedAt();
        session.setDurationSec((int) Math.max(0, Duration.between(started, now).getSeconds()));
        sessionMapper.updateById(session);
        return toVO(session);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<?> mySessions(Long userId, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = Math.min(50, Math.max(1, pageSize));
        Page<MockInterviewSession> result = sessionMapper.selectPage(new Page<>(p, size),
                new LambdaQueryWrapper<MockInterviewSession>()
                        .eq(MockInterviewSession::getUserId, userId)
                        .orderByDesc(MockInterviewSession::getStartedAt)
                        .orderByDesc(MockInterviewSession::getId));
        List<SessionSummaryVO> records = result.getRecords().stream().map(s -> {
            SessionSummaryVO vo = new SessionSummaryVO();
            vo.setId(s.getId());
            vo.setTrack(s.getTrack());
            vo.setTrackName(trackName(s.getTrack()));
            vo.setStatus(s.getStatus());
            vo.setQuestionCount(s.getQuestionCount());
            vo.setAvgSelfScore(s.getAvgSelfScore());
            vo.setDurationSec(s.getDurationSec());
            vo.setStartedAt(s.getStartedAt() == null ? null : s.getStartedAt().toString());
            vo.setFinishedAt(s.getFinishedAt() == null ? null : s.getFinishedAt().toString());
            return vo;
        }).toList();
        PageResult<SessionSummaryVO> out = new PageResult<>();
        out.setRecords(records);
        out.setTotal(result.getTotal());
        out.setPageNum(result.getCurrent());
        out.setPageSize(result.getSize());
        out.setPages(result.getPages());
        return out;
    }

    // ==================== 内部方法 ====================

    private List<Track> availableTracks(Long actor) {
        List<Track> out=new ArrayList<>(InterviewQuestionBank.TRACKS);
        out.addAll(contentBank.tracks(actor));out.addAll(radarBank.tracks(actor));return out;
    }
    private Track findTrack(String code,Long actor) {
        return availableTracks(actor).stream()
                .filter(t -> Objects.equals(t.code(), code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "未知面试方向：" + code));
    }

    private int normalizeCount(Integer requested, int poolSize) {
        int c = requested == null ? DEFAULT_QUESTION_COUNT : requested;
        if (c < 1) {
            throw new BusinessException(400, "题目数量至少为 1");
        }
        c = Math.min(c, MAX_QUESTION_COUNT);
        c = Math.min(c, poolSize);
        return c;
    }

    private MockInterviewSession requireOwnSession(Long userId, Long sessionId) {
        return requireOwnSession(userId, sessionId, false);
    }

    private MockInterviewSession requireOwnSession(Long userId, Long sessionId, boolean lock) {
        if (sessionId == null) {
            throw new BusinessException(404, "面试记录不存在");
        }
        // Save and finish share one current row read: JSON answers cannot be merged from stale snapshots.
        var query = new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getId, sessionId).eq(MockInterviewSession::getUserId, userId);
        if (lock) query.last("FOR UPDATE");
        MockInterviewSession session = sessionMapper.selectOne(query);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            // 他人记录与不存在统一 404，不泄露存在性
            throw new BusinessException(404, "面试记录不存在");
        }
        return session;
    }

    private List<FollowupAnswer> normalizeFollowups(List<FollowupAnswer> input, SeedQuestion target) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        if (input.size() > MAX_FOLLOWUPS) {
            throw new BusinessException(400, "单题追问最多 " + MAX_FOLLOWUPS + " 条");
        }
        Set<String> allowed = Set.copyOf(target.followups());
        List<FollowupAnswer> out = new ArrayList<>();
        for (FollowupAnswer f : input) {
            if (f == null || f.getQ() == null || f.getQ().isBlank()) {
                continue;
            }
            if (!allowed.contains(f.getQ())) {
                throw new BusinessException(400, "追问不属于本题预置追问树");
            }
            if (f.getA() == null || f.getA().isBlank()) {
                // 展开了追问但未作答：忽略该条，不阻塞整题保存
                continue;
            }
            FollowupAnswer clean = new FollowupAnswer();
            clean.setQ(f.getQ());
            clean.setA(trimmed(f.getA(), MAX_FOLLOWUP_CHARS, "追问回答"));
            out.add(clean);
        }
        return out;
    }

    private String trimmed(String raw, int max, String field) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(400, field + "不能为空");
        }
        String v = raw.trim();
        if (v.length() > max) {
            throw new BusinessException(400, field + "超出长度限制（" + max + " 字）");
        }
        return v;
    }

    /** 可选字段：空白归一为 null，超长仍拒绝。 */
    private String optionalTrimmed(String raw, int max, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        if (v.length() > max) {
            throw new BusinessException(400, field + "超出长度限制（" + max + " 字）");
        }
        return v;
    }

    private List<SeedQuestion> readQuestions(MockInterviewSession session) {
        try {
            return objectMapper.readValue(session.getQuestionsJson(),
                    new TypeReference<List<SeedQuestion>>() {
                    });
        } catch (Exception e) {
            throw new BusinessException(500, "面试题目快照读取失败");
        }
    }

    private List<AnswerRequest> readAnswers(MockInterviewSession session) {
        if (session.getAnswersJson() == null || session.getAnswersJson().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(session.getAnswersJson(),
                    new TypeReference<List<AnswerRequest>>() {
                    });
        } catch (Exception e) {
            throw new BusinessException(500, "面试作答读取失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "面试数据序列化失败");
        }
    }

    private SessionVO toVO(MockInterviewSession session) {
        return toVO(session, readQuestions(session), readAnswers(session));
    }

    private SessionVO toVO(MockInterviewSession session, List<SeedQuestion> questions, List<AnswerRequest> answers) {
        SessionVO vo = new SessionVO();
        vo.setId(session.getId());
        vo.setTrack(session.getTrack());
        vo.setTrackName(trackName(session.getTrack()));
        vo.setMode(session.getMode());
        vo.setV2(v2Guard.isV2(session.getId()));
        vo.setStatus(session.getStatus());
        vo.setQuestionCount(session.getQuestionCount());
        vo.setQuestions(questions.stream().map(this::toQuestionVO).toList());
        // 严格模式的参考要点/评分不得通过旧客户端接口提前读取。
        if (session.getTrack().startsWith("RADAR_") || ("STRICT".equals(session.getMode()) && !MockInterviewSession.STATUS_FINISHED.equals(session.getStatus()))) {
            vo.getQuestions().forEach(q -> { q.setReferencePoints(List.of()); q.setRubric(null); q.setFollowups(List.of()); });
        }
        vo.setAnswers(toAnswerVOs(questions, answers));
        vo.setAvgSelfScore(session.getAvgSelfScore());
        vo.setDurationSec(session.getDurationSec());
        vo.setStartedAt(session.getStartedAt() == null ? null : session.getStartedAt().toString());
        vo.setFinishedAt(session.getFinishedAt() == null ? null : session.getFinishedAt().toString());
        vo.setAnsweredCount(answers.size());
        return vo;
    }

    private QuestionVO toQuestionVO(SeedQuestion q) {
        QuestionVO vo = new QuestionVO();
        vo.setId(q.id());
        vo.setTitle(q.title());
        vo.setReferencePoints(q.referencePoints());
        vo.setFollowups(q.followups());
        if (q.rubric() != null) {
            RubricVO r = new RubricVO();
            r.setLevel1(q.rubric().level1());
            r.setLevel3(q.rubric().level3());
            r.setLevel5(q.rubric().level5());
            vo.setRubric(r);
        }
        return vo;
    }

    /** 作答按题目快照顺序输出，便于前端逐题渲染与复盘对照。 */
    private List<AnswerVO> toAnswerVOs(List<SeedQuestion> questions, List<AnswerRequest> answers) {
        Map<String, AnswerRequest> byId = new LinkedHashMap<>();
        for (AnswerRequest a : answers) {
            byId.put(a.getQuestionId(), a);
        }
        List<AnswerVO> out = new ArrayList<>();
        for (SeedQuestion q : questions) {
            AnswerRequest a = byId.get(q.id());
            if (a == null) {
                continue;
            }
            AnswerVO vo = new AnswerVO();
            vo.setQuestionId(q.id());
            vo.setTitle(q.title());
            vo.setAnswer(a.getAnswer());
            vo.setSelfScore(a.getSelfScore());
            vo.setSelfComment(a.getSelfComment());
            vo.setFollowups(Optional.ofNullable(a.getFollowups()).orElse(List.of()).stream().map(f -> {
                FollowupAnswerVO fv = new FollowupAnswerVO();
                fv.setQ(f.getQ());
                fv.setA(f.getA());
                return fv;
            }).toList());
            out.add(vo);
        }
        return out;
    }

    private String trackName(String code) {
        return InterviewQuestionBank.TRACKS.stream()
                .filter(t -> Objects.equals(t.code(), code))
                .map(Track::name).findFirst().orElse(com.knowflow.interview.core.ContentStudioInterviewBank.name(code));
    }
}
