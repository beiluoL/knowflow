package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.CodeMistakeCollectRequest;
import com.knowflow.dto.CodeMistakeCollectResult;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningMistake;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.CodeQuestionMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.service.MistakeService;
import com.knowflow.vo.MistakeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 错题本业务服务实现。 */
@Service
@RequiredArgsConstructor
public class MistakeServiceImpl extends ServiceImpl<LearningMistakeMapper, LearningMistake> implements MistakeService {

    private final DocDocumentMapper docMapper;
    private final CodeQuestionMapper codeQuestionMapper;

    @Override
    public IPage<MistakeVO> getMistakePage(Long userId, String category, Integer mastered, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<LearningMistake> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(LearningMistake::getUserId, userId);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(LearningMistake::getCategory, category);
        }
        if (mastered != null) {
            wrapper.eq(LearningMistake::getMastered, mastered);
        }
        wrapper.orderByDesc(LearningMistake::getCreateTime);

        Page<LearningMistake> page = this.page(new Page<>(com.knowflow.common.PageQuery.normalizePageNum(pageNum), com.knowflow.common.PageQuery.normalizePageSize(pageSize)), wrapper);
        return page.convert(m -> BeanUtil.copyProperties(m, MistakeVO.class));
    }

    @Override
    public MistakeVO getMistakeDetail(Long id, Long userId) {
        LearningMistake mistake = this.getById(id);
        if (mistake == null) {
            throw new BusinessException(404, "错题不存在");
        }
        if (!java.util.Objects.equals(mistake.getUserId(), userId)) {
            throw new BusinessException("无权访问该错题");
        }
        return BeanUtil.copyProperties(mistake, MistakeVO.class);
    }

    @Override
    public void markMastered(Long id, Long userId) {
        LearningMistake mistake = this.getById(id);
        if (mistake == null) {
            throw new BusinessException(404, "错题不存在");
        }
        if (!java.util.Objects.equals(mistake.getUserId(), userId)) {
            throw new BusinessException("无权操作该错题");
        }
        mistake.setMastered(1);
        mistake.setLastReviewTime(LocalDateTime.now());
        this.updateById(mistake);
    }

    @Override
    public void addMistake(LearningMistake mistake, Long userId) {
        // 幂等：同用户 + 同题目（去空白）已存在则更新答案与复习次数，避免反复刷题刷出重复错题
        String question = mistake.getQuestion() == null ? "" : mistake.getQuestion().trim();
        if (!question.isEmpty()) {
            LearningMistake exist = this.getOne(new LambdaQueryWrapper<LearningMistake>()
                    .eq(LearningMistake::getUserId, userId)
                    .eq(LearningMistake::getQuestion, question)
                    .last("LIMIT 1"));
            if (exist != null) {
                exist.setWrongAnswer(mistake.getWrongAnswer());
                exist.setCorrectAnswer(mistake.getCorrectAnswer());
                exist.setCategory(mistake.getCategory());
                exist.setDifficulty(mistake.getDifficulty());
                exist.setSource(mistake.getSource());
                exist.setReviewCount((exist.getReviewCount() == null ? 0 : exist.getReviewCount()) + 1);
                // 再次答错则重置为未掌握，重新进入待复习队列
                exist.setMastered(0);
                exist.setLastReviewTime(LocalDateTime.now());
                this.updateById(exist);
                return;
            }
        }
        mistake.setUserId(userId);
        mistake.setMastered(0);
        mistake.setReviewCount(0);
        mistake.setLastReviewTime(LocalDateTime.now());
        this.save(mistake);
    }

    @Override
    public int getWeeklyNewCount(Long userId) {
        // 本周一 00:00 起至今的错题数
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .ge(LearningMistake::getCreateTime, weekStart)));
    }

    @Override
    public int getDueTodayCount(Long userId) {
        // 待复习：未掌握且（从未复习 或 今天之前已复习过）
        LocalDate today = LocalDate.now();
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getMastered, 0)
                .and(w -> w.isNull(LearningMistake::getLastReviewTime)
                        .or().lt(LearningMistake::getLastReviewTime, today.atStartOfDay()))));
    }

    @Override
    public int getTotalCount(Long userId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)));
    }

    @Override
    public int getMasteredCount(Long userId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getMastered, 1)));
    }

    @Override
    public int getPendingCount(Long userId) {
        return Math.toIntExact(this.count(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getMastered, 0)));
    }

    // ================= 代码错题自动归集（SC1-AI-03） =================

    @Override
    public CodeMistakeCollectResult collectCodeMistake(CodeMistakeCollectRequest req, Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        String lang = req.getLanguage() == null ? "unknown" : req.getLanguage();
        String error = req.getError() == null ? "" : req.getError();
        String code = req.getCode() == null ? "" : req.getCode();

        String type = extractErrorType(error);
        String firstLine = error.lines().findFirst().orElse(error).trim();
        // 用作幂等键的题目摘要：语言 + 错误类型/首行
        String questionKey = "[" + lang + "] " + (type.isEmpty() ? firstLine : type);

        // 关联知识库：用错误类型/关键词匹配文档
        List<CodeMistakeCollectResult.RelatedDoc> docs = matchKnowledgeDocs(type, error);

        // 组装错题（复用 addMistake 的幂等语义：同用户+同 questionKey 去重）
        LearningMistake mistake = new LearningMistake();
        mistake.setUserId(userId);
        mistake.setQuestion(questionKey);
        mistake.setWrongAnswer(truncateLines(code, 12));
        mistake.setCorrectAnswer("");
        mistake.setCategory("代码练习");
        mistake.setSource("code:" + lang + ":" + (type.isEmpty() ? "unknown" : type) + ":"
                + (req.getQuestionId() == null ? "sandbox" : req.getQuestionId()));
        if (req.getQuestionId() != null) {
            com.knowflow.entity.CodeQuestion q = codeQuestionMapper.selectById(req.getQuestionId());
            if (q != null) {
                mistake.setDifficulty(q.getDifficulty());
            }
        }

        LearningMistake exist = this.getOne(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getQuestion, questionKey)
                .last("LIMIT 1"));
        Long mistakeId;
        if (exist != null) {
            exist.setWrongAnswer(mistake.getWrongAnswer());
            exist.setSource(mistake.getSource());
            exist.setReviewCount((exist.getReviewCount() == null ? 0 : exist.getReviewCount()) + 1);
            exist.setMastered(0);
            exist.setLastReviewTime(LocalDateTime.now());
            this.updateById(exist);
            mistakeId = exist.getId();
        } else {
            this.save(mistake);
            mistakeId = mistake.getId();
        }

        return CodeMistakeCollectResult.builder()
                .mistakeId(mistakeId)
                .errorType(type.isEmpty() ? "未知错误" : type)
                .errorSummary(firstLine)
                .collected(true)
                .relatedDocs(docs)
                .build();
    }

    /** 从错误文本中提取已知异常类型；命中则返回该关键字，否则返回空串 */
    private String extractErrorType(String error) {
        if (error == null || error.isBlank()) {
            return "";
        }
        String[] keywords = {
                "NameError", "TypeError", "SyntaxError", "ZeroDivisionError", "IndexError", "KeyError",
                "ValueError", "AttributeError", "IndentationError", "ModuleNotFoundError", "ImportError",
                "NullPointerException", "ArrayIndexOutOfBoundsException", "ClassCastException",
                "IllegalArgumentException", "ArithmeticException", "StackOverflowError",
                "undefined reference", "segmentation fault", "Error", "Exception"
        };
        for (String k : keywords) {
            if (error.contains(k)) {
                return k;
            }
        }
        return "";
    }

    /** 用错误类型/关键词在知识库文档中匹配（标题/正文/标签），返回前 3 条 */
    private List<CodeMistakeCollectResult.RelatedDoc> matchKnowledgeDocs(String type, String error) {
        try {
            String rawKw = (type.isEmpty() ? error : type);
            final String kw = rawKw.length() > 20 ? rawKw.substring(0, 20) : rawKw;
            LambdaQueryWrapper<DocDocument> w = new LambdaQueryWrapper<>();
            w.and(q -> q.like(DocDocument::getTitle, kw)
                    .or().like(DocDocument::getContent, kw)
                    .or().like(DocDocument::getTags, kw));
            w.last("LIMIT 3");
            List<DocDocument> list = docMapper.selectList(w);
            List<CodeMistakeCollectResult.RelatedDoc> res = new ArrayList<>();
            for (DocDocument d : list) {
                res.add(CodeMistakeCollectResult.RelatedDoc.builder()
                        .id(d.getId())
                        .title(d.getTitle())
                        .snippet(truncateLines(d.getContent() == null ? "" : d.getContent(), 2))
                        .build());
            }
            return res;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 取前 maxLines 行，单行超长截断，整体过长追加省略提示 */
    private String truncateLines(String s, int maxLines) {
        if (s == null) {
            return "";
        }
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int n = Math.min(lines.length, maxLines);
        for (int i = 0; i < n; i++) {
            String ln = lines[i];
            if (ln.length() > 200) {
                ln = ln.substring(0, 200) + "...";
            }
            sb.append(ln).append("\n");
        }
        if (lines.length > maxLines) {
            sb.append("...(省略剩余内容)");
        }
        return sb.toString();
    }
}
