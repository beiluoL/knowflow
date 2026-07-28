package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.common.PageResult;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.dto.ReadProgressDTO;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.DocFavorite;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.DocFavoriteMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.AiService;
import com.knowflow.service.CategoryService;
import com.knowflow.service.DocService;
import com.knowflow.vo.DocDetailVO;
import com.knowflow.vo.DocVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 文档业务服务实现。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocServiceImpl extends ServiceImpl<DocDocumentMapper, DocDocument> implements DocService {

    private final DocFavoriteMapper favoriteMapper;
    private final DocReadProgressMapper readProgressMapper;
    private final CategoryService categoryService;
    private final SysUserMapper userMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResult<DocVO> getDocPage(DocQueryDTO dto) {
        Page<DocDocument> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<DocDocument> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(dto.getKeyword())) {
            // F-15 修复：搜索扩展至标签与分类名（分类名先查出命中的分类 id 再并入条件）
            List<Long> matchedCategoryIds = categoryService.list(
                            new LambdaQueryWrapper<DocCategory>().like(DocCategory::getName, dto.getKeyword()))
                    .stream().map(DocCategory::getId).collect(Collectors.toList());
            // 命中顶级分类时，其子分类下的文档也应命中（文档多挂在子分类）
            if (!matchedCategoryIds.isEmpty()) {
                List<Long> childIds = categoryService.list(
                                new LambdaQueryWrapper<DocCategory>().in(DocCategory::getParentId, matchedCategoryIds))
                        .stream().map(DocCategory::getId).collect(Collectors.toList());
                matchedCategoryIds.addAll(childIds);
            }
            wrapper.and(w -> {
                w.like(DocDocument::getTitle, dto.getKeyword())
                        .or().like(DocDocument::getSummary, dto.getKeyword())
                        .or().like(DocDocument::getTags, dto.getKeyword());
                if (!matchedCategoryIds.isEmpty()) {
                    w.or().in(DocDocument::getCategoryId, matchedCategoryIds);
                }
            });
        }
        if (dto.getCategoryId() != null) {
            wrapper.eq(DocDocument::getCategoryId, dto.getCategoryId());
        }
        if (dto.getDifficulty() != null) {
            wrapper.eq(DocDocument::getDifficulty, dto.getDifficulty());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(DocDocument::getStatus, dto.getStatus());
        } else {
            wrapper.eq(DocDocument::getStatus, 1);
        }
        wrapper.orderByDesc(DocDocument::getCreateTime);
        Page<DocDocument> result = this.page(page, wrapper);
        List<DocVO> voList = result.getRecords().stream().map(doc -> {
            DocVO vo = BeanUtil.copyProperties(doc, DocVO.class);
            DocCategory category = categoryService.getById(doc.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
            return vo;
        }).collect(Collectors.toList());
        PageResult<DocVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    public DocDetailVO getDocDetail(Long id, Long userId) {
        DocDocument doc = this.getById(id);
        if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
            // F-14 修复：不存在的文档返回 404 语义
            throw new BusinessException(404, "文档不存在");
        }
        this.update(new LambdaUpdateWrapper<DocDocument>()
                .eq(DocDocument::getId, id)
                .setSql("view_count = view_count + 1"));
        // 计数器已原子自增，重新读取以保证返回值与 DB 一致（避免内存对象残留旧值导致并发下展示滞后）
        doc = this.getById(id);
        DocDetailVO vo = BeanUtil.copyProperties(doc, DocDetailVO.class);
        DocCategory category = categoryService.getById(doc.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
        }
        if (userId != null) {
            DocFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<DocFavorite>()
                    .eq(DocFavorite::getUserId, userId)
                    .eq(DocFavorite::getDocId, id));
            vo.setFavorite(favorite != null);
            DocReadProgress progress = readProgressMapper.selectOne(new LambdaQueryWrapper<DocReadProgress>()
                    .eq(DocReadProgress::getUserId, userId)
                    .eq(DocReadProgress::getDocId, id));
            vo.setReadProgress(progress != null ? progress.getProgress() : BigDecimal.ZERO);
        } else {
            vo.setFavorite(false);
            vo.setReadProgress(BigDecimal.ZERO);
        }
        return vo;
    }

    /** 收藏/取消收藏切换，并同步维护文档与用户的收藏计数（保证非负）。 */
    @Override
    @Transactional
    public void toggleFavorite(Long docId, Long userId) {
        DocDocument doc = this.getById(docId);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        DocFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<DocFavorite>()
                .eq(DocFavorite::getUserId, userId)
                .eq(DocFavorite::getDocId, docId));
        if (favorite != null) {
            favoriteMapper.deleteById(favorite.getId());
            this.update(new LambdaUpdateWrapper<DocDocument>()
                    .eq(DocDocument::getId, docId)
                    .setSql("favorite_count = GREATEST(0, favorite_count - 1)"));
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, userId)
                    .setSql("favorite_count = GREATEST(0, favorite_count - 1)"));
        } else {
            favorite = new DocFavorite();
            favorite.setUserId(userId);
            favorite.setDocId(docId);
            favoriteMapper.insert(favorite);
            this.update(new LambdaUpdateWrapper<DocDocument>()
                    .eq(DocDocument::getId, docId)
                    .setSql("favorite_count = favorite_count + 1"));
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, userId)
                    .setSql("favorite_count = favorite_count + 1"));
        }
    }

    @Override
    public List<DocVO> getFavoriteList(Long userId) {
        List<DocFavorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<DocFavorite>()
                .eq(DocFavorite::getUserId, userId)
                .orderByDesc(DocFavorite::getCreateTime));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> docIds = favorites.stream().map(DocFavorite::getDocId).collect(Collectors.toList());
        List<DocDocument> docs = this.listByIds(docIds);
        return docs.stream().map(doc -> BeanUtil.copyProperties(doc, DocVO.class)).collect(Collectors.toList());
    }

    @Override
    public List<DocVO> getRecentReadList(Long userId) {
        List<DocReadProgress> progresses = readProgressMapper.selectList(new LambdaQueryWrapper<DocReadProgress>()
                .eq(DocReadProgress::getUserId, userId)
                .orderByDesc(DocReadProgress::getLastReadTime)
                .last("LIMIT 10"));
        if (progresses.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> docIds = progresses.stream().map(DocReadProgress::getDocId).collect(Collectors.toList());
        List<DocDocument> docs = this.listByIds(docIds);
        return docs.stream().map(doc -> BeanUtil.copyProperties(doc, DocVO.class)).collect(Collectors.toList());
    }

    @Override
    public List<DocVO> getRecommendList(Long userId) {
        List<DocDocument> docs = this.list(new LambdaQueryWrapper<DocDocument>()
                .eq(DocDocument::getStatus, 1)
                .orderByDesc(DocDocument::getViewCount)
                .orderByDesc(DocDocument::getFavoriteCount)
                .last("LIMIT 10"));
        return docs.stream().map(doc -> BeanUtil.copyProperties(doc, DocVO.class)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateReadProgress(ReadProgressDTO dto, Long userId) {
        DocDocument doc = this.getById(dto.getDocId());
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        DocReadProgress progress = readProgressMapper.selectOne(new LambdaQueryWrapper<DocReadProgress>()
                .eq(DocReadProgress::getUserId, userId)
                .eq(DocReadProgress::getDocId, dto.getDocId()));
        if (progress == null) {
            progress = new DocReadProgress();
            progress.setUserId(userId);
            progress.setDocId(dto.getDocId());
            progress.setProgress(dto.getProgress() != null ? dto.getProgress() : BigDecimal.ZERO);
            progress.setReadSeconds(dto.getReadSeconds() != null ? dto.getReadSeconds() : 0);
            progress.setLastReadTime(LocalDateTime.now());
            readProgressMapper.insert(progress);
        } else {
            if (dto.getProgress() != null) {
                progress.setProgress(dto.getProgress());
            }
            if (dto.getReadSeconds() != null) {
                progress.setReadSeconds(progress.getReadSeconds() + dto.getReadSeconds());
            }
            progress.setLastReadTime(LocalDateTime.now());
            readProgressMapper.updateById(progress);
        }
        if (dto.getProgress() != null && dto.getProgress().compareTo(new BigDecimal("100")) >= 0) {
            this.update(new LambdaUpdateWrapper<DocDocument>()
                    .eq(DocDocument::getId, dto.getDocId())
                    .setSql("read_count = read_count + 1"));
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, userId)
                    .setSql("read_docs_count = read_docs_count + 1"));
        }
    }

    @Override
    @Transactional
    public void saveDoc(DocDocument doc) {
        doc.setWordCount(StrUtil.isNotBlank(doc.getContent()) ? doc.getContent().length() : 0);
        this.save(doc);
        if (doc.getCategoryId() != null) {
            categoryService.incrementDocCount(doc.getCategoryId());
        }
    }

    @Override
    @Transactional
    public void updateDoc(DocDocument doc) {
        DocDocument old = this.getById(doc.getId());
        doc.setWordCount(StrUtil.isNotBlank(doc.getContent()) ? doc.getContent().length() : 0);
        this.updateById(doc);
        if (old != null) {
            Long oldCat = old.getCategoryId();
            Long newCat = doc.getCategoryId();
            if (oldCat != null && !oldCat.equals(newCat)) {
                categoryService.decrementDocCount(oldCat);
                if (newCat != null) {
                    categoryService.incrementDocCount(newCat);
                }
            }
        }
    }

    @Override
    @Transactional
    public void removeDoc(Long id) {
        DocDocument doc = this.getById(id);
        favoriteMapper.delete(new LambdaQueryWrapper<DocFavorite>().eq(DocFavorite::getDocId, id));
        readProgressMapper.delete(new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getDocId, id));
        if (doc != null && doc.getCategoryId() != null) {
            categoryService.decrementDocCount(doc.getCategoryId());
        }
        this.removeById(id);
    }

    /** B③ AI 生成文档摘要：截断正文后调用大模型，回填 doc.summary 并返回。 */
    @Override
    public String generateAISummary(Long docId) {
        DocDocument doc = this.getById(docId);
        if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
            throw new BusinessException(404, "文档不存在");
        }
        String content = doc.getContent() != null ? doc.getContent() : "";
        if (content.length() > 4000) {
            content = content.substring(0, 4000);
        }
        String userPrompt = "请为以下文章生成一个简洁的中文摘要（不超过150字），概括核心内容与要点，"
                + "只输出摘要正文，不要使用 Markdown 或多余解释：\n\n标题：" + doc.getTitle()
                + "\n\n正文：\n" + content;
        String summary = aiService.complete(
                "你是一个擅长提炼要点的写作助手。", userPrompt);
        if (summary != null) {
            summary = summary.trim();
        }
        doc.setSummary(summary);
        this.updateById(doc);
        return summary;
    }

    /** B③ AI 基于文档生成复习闪卡：解析大模型返回的 JSON，批量落库后返回。 */
    @Override
    @Transactional
    public List<LearningFlashcard> generateFlashcards(Long docId, Long pathId, Long chapterId) {
        DocDocument doc = this.getById(docId);
        if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
            throw new BusinessException(404, "文档不存在");
        }
        String content = doc.getContent() != null ? doc.getContent() : "";
        if (content.length() > 4000) {
            content = content.substring(0, 4000);
        }
        String userPrompt = "基于以下文章，生成 5 张复习闪卡，每张卡为一个「问答对」。\n"
                + "返回严格 JSON 数组，元素格式：{\"front\":\"问题\",\"back\":\"答案\",\"difficulty\":1}\n"
                + "difficulty 取值 1(简单)/2(中等)/3(困难)。不要输出 JSON 以外的任何文字。\n\n"
                + "标题：" + doc.getTitle() + "\n\n正文：\n" + content;
        String raw = aiService.complete("你是知识卡片生成助手，只输出符合要求的 JSON。", userPrompt);
        List<LearningFlashcard> cards = parseFlashcards(raw, doc, pathId, chapterId);
        if (cards.isEmpty()) {
            throw new BusinessException("AI 未返回有效闪卡，请重试或调整文档内容");
        }
        for (LearningFlashcard card : cards) {
            flashcardMapper.insert(card);
        }
        return cards;
    }

    /** 解析大模型返回的闪卡 JSON（兼容 ```json 围栏），构建闪卡实体列表。 */
    private List<LearningFlashcard> parseFlashcards(String raw, DocDocument doc, Long pathId, Long chapterId) {
        List<LearningFlashcard> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        String json = raw.trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return result;
        }
        json = json.substring(start, end + 1);
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, List.class);
            String category = null;
            if (doc.getCategoryId() != null) {
                DocCategory cat = categoryService.getById(doc.getCategoryId());
                if (cat != null) {
                    category = cat.getName();
                }
            }
            for (Map<String, Object> item : list) {
                Object front = item.get("front");
                Object back = item.get("back");
                if (front == null || back == null) {
                    continue;
                }
                LearningFlashcard card = new LearningFlashcard();
                card.setFront(String.valueOf(front));
                card.setBack(String.valueOf(back));
                int d = 1;
                Object diff = item.get("difficulty");
                if (diff instanceof Number) {
                    d = ((Number) diff).intValue();
                }
                if (d < 1 || d > 3) {
                    d = 1;
                }
                card.setDifficulty(d);
                card.setCategory(category);
                card.setPathId(pathId);
                card.setChapterId(chapterId);
                card.setReviewCount(0);
                result.add(card);
            }
        } catch (Exception e) {
            log.error("解析闪卡 JSON 失败: {}", e.getMessage());
        }
        return result;
    }
}
