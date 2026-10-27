package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.common.PageResult;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.dto.DocUploadMetaDTO;
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
import com.knowflow.service.DocChunkService;
import com.knowflow.service.DocService;
import com.knowflow.common.LearningEventType;
import com.knowflow.service.LearningEventService;
import com.knowflow.service.DocumentTextExtractor;
import com.knowflow.service.KnowledgeService;
import com.knowflow.util.UploadHelper;
import com.knowflow.config.UploadConfigProperties;
import com.knowflow.vo.DocDetailVO;
import com.knowflow.vo.DocVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** 文档业务服务实现。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocServiceImpl extends ServiceImpl<DocDocumentMapper, DocDocument> implements DocService {

    private final DocFavoriteMapper favoriteMapper;
    private final com.knowflow.security.DocumentAssetBindings assetBindings;
    private final DocReadProgressMapper readProgressMapper;
    private final CategoryService categoryService;
    private final SysUserMapper userMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final AiService aiService;
    private final DocChunkService docChunkService;
    private final DocumentTextExtractor documentTextExtractor;
    private final KnowledgeService knowledgeService;
    private final UploadConfigProperties uploadConfig;
    private final LearningEventService learningEventService;
    private final com.knowflow.security.DocumentAccessPolicy access;
    private final com.knowflow.mapper.LearningPathMapper learningPathMapper;
    private final com.knowflow.mapper.LearningChapterMapper learningChapterMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============ 搜索排序与相关度打分参数 ============
    /** 排序方式：相关度 */
    private static final String SORT_RELEVANCE = "relevance";
    /** 排序方式：最热（按阅读量） */
    private static final String SORT_VIEW = "view";
    /** 相关度排序的候选集上限，防止大数据量下全表加载进内存 */
    private static final int RELEVANCE_CANDIDATE_LIMIT = 500;
    /** 字段权重：标题命中 */
    private static final double WEIGHT_TITLE = 10.0;
    /** 字段权重：标题完全相等的额外加成 */
    private static final double WEIGHT_TITLE_EXACT = 20.0;
    /** 字段权重：标题前缀匹配的额外加成 */
    private static final double WEIGHT_TITLE_PREFIX = 5.0;
    /** 字段权重：标签命中 */
    private static final double WEIGHT_TAGS = 6.0;
    /** 字段权重：摘要命中 */
    private static final double WEIGHT_SUMMARY = 3.0;
    /** 字段权重：正文单次命中 */
    private static final double WEIGHT_CONTENT = 1.0;
    /** 正文命中计分上限，避免长文档靠篇幅堆分 */
    private static final int MAX_CONTENT_HITS = 5;
    /** 摘要片段：关键词前保留字符数 */
    private static final int SNIPPET_BEFORE = 40;
    /** 摘要片段：关键词后保留字符数 */
    private static final int SNIPPET_AFTER = 120;
    /** 语义通道召回文档数上限 */
    private static final int SEMANTIC_RECALL_LIMIT = 50;
    /** RRF 融合常数，业界经验值 60，抑制头部排名的过度影响 */
    private static final int RRF_K = 60;

    @Override
    public PageResult<DocVO> getDocPage(DocQueryDTO dto) {
        boolean hasKeyword = StrUtil.isNotBlank(dto.getKeyword());
        String keyword = hasKeyword ? dto.getKeyword().trim() : null;
        // 相关度排序需在应用层按命中位置打分，无法交给 SQL 完成，
        // 因此走「宽召回 + 内存打分 + 手动分页」；其余排序仍用数据库分页避免全量加载。
        boolean relevanceSort = hasKeyword && isRelevanceSort(dto.getSort());

        LambdaQueryWrapper<DocDocument> wrapper = new LambdaQueryWrapper<>();
        access.scope(wrapper, access.actor());
        if (hasKeyword) {
            List<Long> matchedCategoryIds = findMatchedCategoryIds(keyword);
            wrapper.and(w -> {
                w.like(DocDocument::getTitle, keyword)
                        .or().like(DocDocument::getSummary, keyword)
                        .or().like(DocDocument::getTags, keyword)
                        // 正文纳入检索范围：此前遗漏导致「搜正文关键词返回空」
                        .or().like(DocDocument::getContent, keyword);
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
        applyTypeFilter(wrapper, dto.getType());
        applyTimeRange(wrapper, dto.getFrom(), dto.getTo());

        return relevanceSort
                ? pageByRelevance(dto, keyword, wrapper)
                : pageByColumn(dto, keyword, wrapper);
    }

    /**
     * 类型过滤下推（替代历史前端 tags 子串过滤，保证分页/total 正确）。
     * 口径与旧前端保持一致：pdf/markdown 看文件名后缀与 tags，note 看 tags，doc 为「非笔记」。
     */
    private void applyTypeFilter(LambdaQueryWrapper<DocDocument> wrapper, String type) {
        if (StrUtil.isBlank(type)) {
            return;
        }
        switch (type.trim().toLowerCase()) {
            case "pdf" -> wrapper.apply("LOWER(file_name) LIKE {0}", "%.pdf");
            case "markdown" -> wrapper.and(w -> w.apply("LOWER(file_name) LIKE {0}", "%.md")
                    .or().like(DocDocument::getTags, "markdown")
                    .or().apply("LOWER(tags) LIKE {0}", "%md%"));
            case "note" -> wrapper.and(w -> w.like(DocDocument::getTags, "笔记")
                    .or().apply("LOWER(tags) LIKE {0}", "%note%"));
            // doc = 非笔记（含 tags 为空的普通文档），NULL 必须显式放行
            case "doc" -> wrapper.and(w -> w.isNull(DocDocument::getTags)
                    .or(o -> o.notLike(DocDocument::getTags, "笔记")
                            .apply("LOWER(tags) NOT LIKE {0}", "%note%")));
            default -> throw new com.knowflow.exception.BusinessException(400, "非法的文档类型过滤：" + type);
        }
    }

    /** 创建时间范围过滤（含边界）；from/to 为 yyyy-MM-dd，非法格式直接 400，不做静默忽略。 */
    private void applyTimeRange(LambdaQueryWrapper<DocDocument> wrapper, String from, String to) {
        if (StrUtil.isNotBlank(from)) {
            try {
                wrapper.ge(DocDocument::getCreateTime, java.time.LocalDate.parse(from.trim()).atStartOfDay());
            } catch (java.time.format.DateTimeParseException e) {
                throw new com.knowflow.exception.BusinessException(400, "from 必须是 yyyy-MM-dd 日期");
            }
        }
        if (StrUtil.isNotBlank(to)) {
            try {
                wrapper.lt(DocDocument::getCreateTime,
                        java.time.LocalDate.parse(to.trim()).plusDays(1).atStartOfDay());
            } catch (java.time.format.DateTimeParseException e) {
                throw new com.knowflow.exception.BusinessException(400, "to 必须是 yyyy-MM-dd 日期");
            }
        }
    }

    /** 缺省排序策略：有关键词时按相关度，无关键词时按时间 */
    private boolean isRelevanceSort(String sort) {
        return StrUtil.isBlank(sort) || SORT_RELEVANCE.equalsIgnoreCase(sort);
    }

    /**
     * 查出分类名命中关键词的分类 id 集合（含其直接子分类）。
     * 文档通常挂在子分类下，命中父分类时子分类文档也应召回。
     */
    private List<Long> findMatchedCategoryIds(String keyword) {
        List<Long> matched = categoryService.list(
                        new LambdaQueryWrapper<DocCategory>().like(DocCategory::getName, keyword))
                .stream().map(DocCategory::getId).collect(Collectors.toList());
        if (matched.isEmpty()) {
            return matched;
        }
        List<Long> childIds = categoryService.list(
                        new LambdaQueryWrapper<DocCategory>().in(DocCategory::getParentId, matched))
                .stream().map(DocCategory::getId).collect(Collectors.toList());
        matched.addAll(childIds);
        return matched;
    }

    /** 普通排序：数据库分页 */
    private PageResult<DocVO> pageByColumn(DocQueryDTO dto, String keyword,
                                           LambdaQueryWrapper<DocDocument> wrapper) {
        if (SORT_VIEW.equalsIgnoreCase(dto.getSort())) {
            // id 兜底保证并列值翻页稳定，避免跨页漂移
            wrapper.orderByDesc(DocDocument::getViewCount);
            wrapper.orderByDesc(DocDocument::getId);
        } else {
            wrapper.orderByDesc(DocDocument::getCreateTime);
            wrapper.orderByDesc(DocDocument::getId);
        }
        Page<DocDocument> result = this.page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
        List<DocVO> voList = toVoList(result.getRecords(), keyword, false);

        PageResult<DocVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    /**
     * 相关度排序（混合检索）：
     * 1) 关键词通道——按创建时间倒序宽召回候选集（上限 RELEVANCE_CANDIDATE_LIMIT），按字段权重打分；
     * 2) 语义通道——向量相似度召回文档 id（embedding 不可用时自动跳过）；
     * 3) 用 RRF（Reciprocal Rank Fusion）融合两路排名，再手动切片分页。
     *
     * <p>RRF 只依赖排名而非绝对分值，天然规避了「关键词得分」与「余弦相似度」量纲不一致的问题。
     * 候选集设上限是为了避免大数据量下把整表拉进 JVM。
     */
    private PageResult<DocVO> pageByRelevance(DocQueryDTO dto, String keyword,
                                              LambdaQueryWrapper<DocDocument> wrapper) {
        wrapper.orderByDesc(DocDocument::getCreateTime);
        wrapper.orderByDesc(DocDocument::getId);
        Page<DocDocument> candidatePage = this.page(new Page<>(1, RELEVANCE_CANDIDATE_LIMIT), wrapper);
        List<DocDocument> candidates = candidatePage.getRecords();

        String lowerKeyword = keyword.toLowerCase();
        // 关键词通道排名（id 作为最终并列兜底，保证翻页确定性）
        List<DocDocument> keywordRanked = candidates.stream()
                .sorted(Comparator.comparingDouble((DocDocument d) -> -relevanceScore(d, lowerKeyword))
                        .thenComparing(DocDocument::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocDocument::getId))
                .collect(Collectors.toList());

        List<DocDocument> sorted = fuseWithSemantic(keywordRanked, keyword);

        // 诚实窗口：相关度打分只覆盖最新 RELEVANCE_CANDIDATE_LIMIT 条候选。
        // 命中量超出窗口时，total 必须截断到窗口大小，否则会出现「total 很大、深页为空」的伪分页。
        long rawTotal = candidatePage.getTotal();
        long total = Math.min(rawTotal, sorted.size());
        int pageNum = Math.max(1, (int) dto.getPageNum());
        int pageSize = Math.max(1, (int) dto.getPageSize());
        int from = Math.min((pageNum - 1) * pageSize, sorted.size());
        int to = Math.min(from + pageSize, sorted.size());
        List<DocVO> voList = toVoList(sorted.subList(from, to), keyword, true);

        PageResult<DocVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(total);
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        pageResult.setPages(pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize);
        return pageResult;
    }

    /**
     * 将关键词排名与语义排名做 RRF 融合。
     *
     * <p>RRF 公式：score(d) = Σ 1 / (k + rank_i(d))，k 取 60 为业界常用经验值。
     * 语义通道不可用（未配置 embedding / 无向量数据）时原样返回关键词排名，
     * 保证功能降级而非报错。
     *
     * <p>只对已在关键词候选集中的文档做重排，不引入候选集之外的文档，
     * 以保证分页 total 与实际结果集一致。
     */
    private List<DocDocument> fuseWithSemantic(List<DocDocument> keywordRanked, String keyword) {
        Map<Long, Double> semanticScores;
        try {
            semanticScores = docChunkService.searchSimilarDocIds(keyword, SEMANTIC_RECALL_LIMIT);
        } catch (Exception e) {
            log.warn("语义召回失败，退化为纯关键词排序: {}", e.getMessage());
            return keywordRanked;
        }
        if (semanticScores == null || semanticScores.isEmpty()) {
            return keywordRanked;
        }

        // 语义通道排名：LinkedHashMap 已按得分降序，遍历顺序即排名
        Map<Long, Integer> semanticRank = new java.util.HashMap<>();
        int rank = 0;
        for (Long docId : semanticScores.keySet()) {
            semanticRank.put(docId, rank++);
        }
        // 关键词通道排名
        Map<Long, Integer> keywordRank = new java.util.HashMap<>();
        for (int i = 0; i < keywordRanked.size(); i++) {
            keywordRank.put(keywordRanked.get(i).getId(), i);
        }

        Map<Long, Double> fused = new java.util.HashMap<>();
        for (DocDocument doc : keywordRanked) {
            double score = 0;
            Integer kr = keywordRank.get(doc.getId());
            if (kr != null) {
                score += 1.0 / (RRF_K + kr);
            }
            Integer sr = semanticRank.get(doc.getId());
            if (sr != null) {
                score += 1.0 / (RRF_K + sr);
            }
            fused.put(doc.getId(), score);
        }

        return keywordRanked.stream()
                .sorted(Comparator.comparingDouble((DocDocument d) -> -fused.getOrDefault(d.getId(), 0.0)))
                .collect(Collectors.toList());
    }

    /**
     * 相关度打分：按字段重要性加权，标题 > 标签 > 摘要 > 正文。
     * 标题完全相等或前缀匹配额外加权，使精确命中稳定排在前面。
     */
    private double relevanceScore(DocDocument doc, String lowerKeyword) {
        double score = 0;
        String title = doc.getTitle() == null ? "" : doc.getTitle().toLowerCase();
        if (!title.isEmpty() && title.contains(lowerKeyword)) {
            score += WEIGHT_TITLE;
            if (title.equals(lowerKeyword)) {
                score += WEIGHT_TITLE_EXACT;
            } else if (title.startsWith(lowerKeyword)) {
                score += WEIGHT_TITLE_PREFIX;
            }
        }
        String tags = doc.getTags() == null ? "" : doc.getTags().toLowerCase();
        if (!tags.isEmpty() && tags.contains(lowerKeyword)) {
            score += WEIGHT_TAGS;
        }
        String summary = doc.getSummary() == null ? "" : doc.getSummary().toLowerCase();
        if (!summary.isEmpty() && summary.contains(lowerKeyword)) {
            score += WEIGHT_SUMMARY;
        }
        String content = doc.getContent() == null ? "" : doc.getContent().toLowerCase();
        if (!content.isEmpty()) {
            // 正文按出现频次累加，但设上限避免长文档仅靠篇幅堆分
            int occurrences = countOccurrences(content, lowerKeyword);
            score += Math.min(occurrences, MAX_CONTENT_HITS) * WEIGHT_CONTENT;
        }
        return score;
    }

    /** 统计子串出现次数（非重叠） */
    private int countOccurrences(String text, String keyword) {
        if (keyword.isEmpty()) return 0;
        int count = 0;
        int idx = text.indexOf(keyword);
        while (idx >= 0 && count < MAX_CONTENT_HITS) {
            count++;
            idx = text.indexOf(keyword, idx + keyword.length());
        }
        return count;
    }

    /**
     * 批量组装 VO：一次性查出所有分类名，消除逐条 getById 的 N+1 查询。
     *
     * @param withScore 是否回填相关度得分（仅相关度排序时有意义）
     */
    private List<DocVO> toVoList(List<DocDocument> docs, String keyword, boolean withScore) {
        if (docs == null || docs.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> categoryIds = docs.stream()
                .map(DocDocument::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> categoryNameMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryService.listByIds(categoryIds).stream()
                        .collect(Collectors.toMap(DocCategory::getId, DocCategory::getName, (a, b) -> a));

        String lowerKeyword = keyword == null ? null : keyword.toLowerCase();
        return docs.stream().map(doc -> {
            DocVO vo = BeanUtil.copyProperties(doc, DocVO.class);
            if (doc.getCategoryId() != null) {
                vo.setCategoryName(categoryNameMap.get(doc.getCategoryId()));
            }
            if (lowerKeyword != null) {
                vo.setHighlight(buildSnippet(doc.getContent(), keyword));
                if (withScore) {
                    vo.setScore(relevanceScore(doc, lowerKeyword));
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 截取正文中关键词周围的上下文片段，返回纯文本。
     * 不拼接 HTML 标签：高亮由前端在转义后渲染，避免后端注入 HTML 造成 XSS。
     */
    private String buildSnippet(String content, String keyword) {
        if (StrUtil.isBlank(content) || StrUtil.isBlank(keyword)) {
            return null;
        }
        // 去除 markdown 换行与多余空白，避免片段里出现大段空行
        String flat = content.replaceAll("\\s+", " ").trim();
        int idx = flat.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return null;
        }
        int from = Math.max(0, idx - SNIPPET_BEFORE);
        int to = Math.min(flat.length(), idx + keyword.length() + SNIPPET_AFTER);
        String snippet = flat.substring(from, to);
        if (from > 0) {
            snippet = "..." + snippet;
        }
        if (to < flat.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    @Override
    public DocDetailVO getDocDetail(Long id, Long userId) {
        DocDocument doc = this.getById(id);
        access.requireRead(doc,userId);
        this.update(new LambdaUpdateWrapper<DocDocument>()
                .eq(DocDocument::getId, id)
                .setSql("view_count = view_count + 1"));
        // 计数器已原子自增，重新读取以保证返回值与 DB 一致（避免内存对象残留旧值导致并发下展示滞后）
        doc = this.getById(id);
        return buildDetailVO(doc, userId);
    }

    /** 组装文档详情 VO（不含阅读量自增，供详情查询与上传复用）。 */
    private DocDetailVO buildDetailVO(DocDocument doc, Long userId) {
        DocDetailVO vo = BeanUtil.copyProperties(doc, DocDetailVO.class);
        vo.setCanEdit(access.canEditCategory(doc.getCategoryId(),userId));
        DocCategory category = categoryService.getById(doc.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
        }
        if (userId != null) {
            DocFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<DocFavorite>()
                    .eq(DocFavorite::getUserId, userId)
                    .eq(DocFavorite::getDocId, doc.getId()));
            vo.setFavorite(favorite != null);
            DocReadProgress progress = readProgressMapper.selectOne(new LambdaQueryWrapper<DocReadProgress>()
                    .eq(DocReadProgress::getUserId, userId)
                    .eq(DocReadProgress::getDocId, doc.getId()));
            vo.setReadProgress(progress != null ? progress.getProgress() : BigDecimal.ZERO);
        } else {
            vo.setFavorite(false);
            vo.setReadProgress(BigDecimal.ZERO);
        }
        return vo;
    }

    /** 上传文件大小上限：50MB，与 application.yml spring.servlet.multipart.max-file-size 保持一致。 */
    private static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;

    @Override
    @Transactional
    public DocDetailVO uploadDoc(MultipartFile file, DocUploadMetaDTO meta, Long userId) {
        if(meta==null) throw new BusinessException(400,"文档元信息不能为空");
        access.requireEditCategory(meta.getCategoryId(),userId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        // 应用层大小校验：即使 Spring multipart 已拦截，这里给出更友好的业务错误文案
        long size = file.getSize();
        if (size > MAX_UPLOAD_BYTES) {
            long mb = MAX_UPLOAD_BYTES / (1024 * 1024);
            throw new BusinessException(413,
                    "文件过大（" + formatMB(size) + "MB），最大支持 " + mb + "MB");
        }
        String originalName = file.getOriginalFilename();
        // 1. 原文落盘（支持后续原文下载/预览）；落盘失败不阻断正文抽取与入库
        String fileUrl = null;
        try {
            Map<String, Object> saved = UploadHelper.save(file, uploadConfig.getDir());
            fileUrl = (String) saved.get("fileUrl");
        } catch (IOException e) {
            log.warn("文档原文落盘失败：{}", originalName, e);
        }
        // 2. 抽取正文（基于内容自动探测类型，PDF/DOC/DOCX/PPT 等统一处理；内置 60s 超时兜底）
        long extractStart = System.nanoTime();
        String content = documentTextExtractor.extractText(file);
        long extractMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - extractStart);
        int words = StrUtil.isNotBlank(content) ? content.length() : 0;
        log.info("文档解析完成：name={}, size={}B, words={}, cost={}ms",
                originalName, size, words, extractMs);
        // 3. 组装并入库
        DocDocument doc = new DocDocument();
        doc.setFileName(originalName);
        doc.setFileSize(size);
        doc.setTitle(StrUtil.isNotBlank(meta.getTitle())
                ? meta.getTitle().trim()
                : (originalName != null ? originalName : "未命名文档"));
        doc.setSummary(meta.getSummary());
        doc.setTags(meta.getTags());
        doc.setCategoryId(meta.getCategoryId());
        doc.setContent(content);
        doc.setFileUrl(fileUrl);
        doc.setStatus(meta.getStatus() != null ? meta.getStatus() : 1);
        doc.setDifficulty(meta.getDifficulty());
        doc.setWordCount(words);
        this.save(doc);
        assetBindings.trustedAsset(fileUrl,doc.getId());
        // A-RAG：文档保存后自动分块并生成 embedding 索引
        if (StrUtil.isNotBlank(doc.getContent())) {
            try {
                docChunkService.indexDocument(doc.getId(), doc.getContent());
            } catch (Exception e) {
                log.warn("文档分块索引失败（不影响文档上传）: docId={}, {}", doc.getId(), e.getMessage());
            }
            // A-RAG-04：文档保存后抽取实体+关系，构建知识图谱（best-effort，失败不影响上传）
            try {
                knowledgeService.extractDoc(doc.getId());
            } catch (Exception e) {
                log.warn("文档实体关系抽取失败（不影响文档上传）: docId={}, {}", doc.getId(), e.getMessage());
            }
        }
        if (doc.getCategoryId() != null) {
            categoryService.incrementDocCount(doc.getCategoryId());
        }
        return buildDetailVO(doc, userId);
    }

    private static String formatMB(long bytes) {
        long mb = bytes / (1024 * 1024);
        long rem = (bytes % (1024 * 1024)) / (100 * 1024); // 保留 1 位小数的整数形式
        if (mb <= 0) {
            return String.valueOf(bytes / 1024) + "KB / ~";
        }
        return mb + "." + (rem / 10);
    }

    /**
     * 收藏/取消收藏切换，并同步维护文档与用户的收藏计数（保证非负）。
     *
     * <p>取消收藏是<b>逻辑删除</b>，而唯一约束 {@code uk_fav_user_doc(user_id, doc_id)} 不含
     * {@code deleted} 列 —— 残留行会一直占着唯一键。因此「再次收藏」不能直接插入新行，
     * 必须先按 {@link DocFavoriteMapper#reviveDeleted} 把原行复活（{@code deleted} 置回 0）；
     * 只有在历史上从未收藏过（复活影响 0 行）时才插入。</p>
     */
    @Override
    @Transactional
    public void toggleFavorite(Long docId, Long userId) {
        DocDocument doc = this.getById(docId);
        access.requireRead(doc,userId);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        DocFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<DocFavorite>()
                .eq(DocFavorite::getUserId, userId)
                .eq(DocFavorite::getDocId, docId));
        if (favorite != null) {
            // 以「实际被逻辑删除的行数」为准再回退计数：并发双删时第二次影响 0 行，
            // 若仍无条件 -1 会把 favorite_count 减穿（GREATEST 只会掩盖而非修正）。
            if (favoriteMapper.deleteById(favorite.getId()) > 0) {
                this.update(new LambdaUpdateWrapper<DocDocument>()
                        .eq(DocDocument::getId, docId)
                        .setSql("favorite_count = GREATEST(0, favorite_count - 1)"));
                userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                        .eq(SysUser::getId, userId)
                        .setSql("favorite_count = GREATEST(0, favorite_count - 1)"));
            }
        } else {
            // 再次收藏：优先复活历史软删行。取消收藏只把 deleted 置 1，物理行仍占着
            // uk_fav_user_doc(user_id, doc_id)，直接 insert 必撞唯一键 → DuplicateKeyException → 500，
            // 且此后该 (user, doc) 永久无法再收藏。复活失败（影响 0 行）说明历史上从未收藏过，才新增。
            int revived = favoriteMapper.reviveDeleted(userId, docId, LocalDateTime.now());
            if (revived == 0) {
                favorite = new DocFavorite();
                favorite.setUserId(userId);
                favorite.setDocId(docId);
                try {
                    favoriteMapper.insert(favorite);
                } catch (DuplicateKeyException e) {
                    // 并发重复收藏：唯一索引兜底，视为已收藏，直接返回以免计数被二次累加
                    log.debug("忽略并发重复收藏：userId={}, docId={}", userId, docId);
                    return;
                }
            }
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
        if(userId==null) return List.of();
        List<DocFavorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<DocFavorite>()
                .eq(DocFavorite::getUserId, userId)
                .orderByDesc(DocFavorite::getCreateTime));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> docIds = favorites.stream().map(DocFavorite::getDocId).collect(Collectors.toList());
        List<DocDocument> docs = this.listByIds(docIds).stream().filter(d->access.canRead(d,userId)).toList();
        return docs.stream().map(doc -> BeanUtil.copyProperties(doc, DocVO.class)).collect(Collectors.toList());
    }

    @Override
    public List<DocVO> getRecentReadList(Long userId) {
        if(userId==null) return List.of();
        List<DocReadProgress> progresses = readProgressMapper.selectList(new LambdaQueryWrapper<DocReadProgress>()
                .eq(DocReadProgress::getUserId, userId)
                .orderByDesc(DocReadProgress::getLastReadTime)
                .last("LIMIT 10"));
        if (progresses.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> docIds = progresses.stream().map(DocReadProgress::getDocId).collect(Collectors.toList());
        List<DocDocument> docs = this.listByIds(docIds).stream().filter(d->access.canRead(d,userId)).toList();
        return docs.stream().map(doc -> BeanUtil.copyProperties(doc, DocVO.class)).collect(Collectors.toList());
    }

    @Override
    public List<DocVO> getRecommendList(Long userId) {
        List<DocDocument> docs = this.list(access.scope(new LambdaQueryWrapper<DocDocument>(),userId)
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
        access.requireRead(doc,userId);
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
        learningEventService.record(userId, LearningEventType.DOCUMENT_READ, "DOC", dto.getDocId(), null);
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
        access.requireEditCategory(doc.getCategoryId(),access.actor());
        sanitize(doc,true);
        assetBindings.clientReferences(doc);
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
        access.requireEdit(old,access.actor());
        if(doc.getCategoryId()!=null) access.requireEditCategory(doc.getCategoryId(),access.actor());
        assetBindings.retain(old);
        sanitize(doc,false);
        assetBindings.clientReferences(doc);
        // 仅当传入 content 时才重算 wordCount，避免仅更新状态时把 wordCount 重置为 0
        if (doc.getContent()!=null) {
            doc.setWordCount(doc.getContent().length());
        } else {
            doc.setWordCount(null);
        }
        this.updateById(doc);
        if (old != null) {
            Long oldCat = old.getCategoryId();
            Long newCat = doc.getCategoryId()!=null ? doc.getCategoryId() : oldCat;
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
        access.requireEdit(doc,access.actor());
        favoriteMapper.delete(new LambdaQueryWrapper<DocFavorite>().eq(DocFavorite::getDocId, id));
        readProgressMapper.delete(new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getDocId, id));
        docChunkService.deleteChunks(id);
        if (doc != null && doc.getCategoryId() != null) {
            categoryService.decrementDocCount(doc.getCategoryId());
        }
        this.removeById(id);
    }

    @Override
    @Transactional
    public void batchDeleteDocs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 查询所有待删除文档（用于维护分类文档数）
        List<DocDocument> docs = this.listByIds(ids);
        requireEditableBatch(ids,docs);
        // 删除关联的收藏与阅读进度
        favoriteMapper.delete(new LambdaQueryWrapper<DocFavorite>().in(DocFavorite::getDocId, ids));
        readProgressMapper.delete(new LambdaQueryWrapper<DocReadProgress>().in(DocReadProgress::getDocId, ids));
        ids.forEach(docChunkService::deleteChunks);
        // 按分类分组统计并扣减文档数
        Map<Long, Long> catCountMap = docs.stream()
                .filter(d -> d.getCategoryId() != null)
                .collect(Collectors.groupingBy(DocDocument::getCategoryId, Collectors.counting()));
        catCountMap.forEach((catId, cnt) -> {
            for (long i = 0; i < cnt; i++) {
                categoryService.decrementDocCount(catId);
            }
        });
        this.removeByIds(ids);
    }

    @Override
    @Transactional
    public void batchMoveDocs(List<Long> ids, Long categoryId) {
        if (ids == null || ids.isEmpty() || categoryId == null) {
            throw new BusinessException(400, "参数不能为空");
        }
        // 校验目标分类存在
        DocCategory target = categoryService.getById(categoryId);
        if (target == null) {
            throw new BusinessException(404, "目标知识库不存在");
        }
        // 查询所有文档，统计各原分类需要扣减的数量
        List<DocDocument> docs = this.listByIds(ids);
        requireEditableBatch(ids,docs);
        access.requireEditCategory(categoryId,access.actor());
        // 只统计目标分类之外的（同分类不扣减）
        Map<Long, Long> sourceCatCountMap = docs.stream()
                .filter(d -> d.getCategoryId() != null && !categoryId.equals(d.getCategoryId()))
                .collect(Collectors.groupingBy(DocDocument::getCategoryId, Collectors.counting()));
        // 批量更新 categoryId
        this.update(new LambdaUpdateWrapper<DocDocument>()
                .in(DocDocument::getId, ids)
                .set(DocDocument::getCategoryId, categoryId));
        // 扣减源分类文档数
        sourceCatCountMap.forEach((catId, cnt) -> {
            for (long i = 0; i < cnt; i++) {
                categoryService.decrementDocCount(catId);
            }
        });
        // 增加目标分类文档数
        long movedCount = docs.stream()
                .filter(d -> !categoryId.equals(d.getCategoryId()))
                .count();
        for (long i = 0; i < movedCount; i++) {
            categoryService.incrementDocCount(categoryId);
        }
    }

    /** B③ AI 生成文档摘要：截断正文后调用大模型，回填 doc.summary 并返回。 */
    @Override
    public String generateAISummary(Long docId) {
        DocDocument doc = this.getById(docId);
        access.requireEdit(doc,access.actor());
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

    /**
     * B③ AI 基于文档生成复习闪卡：解析大模型返回的 JSON，批量落库后返回。
     *
     * <p>P0-1b：闪卡归属由 {@code userId} 决定（来自登录态，非前端可控）。
     * {@code learning_flashcard.user_id} 为 {@code NOT NULL}，缺失时直接 401 而非落库失败。</p>
     */
    @Override
    @Transactional
    public List<LearningFlashcard> generateFlashcards(Long docId, Long pathId, Long chapterId, Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        DocDocument doc = this.getById(docId);
        access.requireRead(doc,userId);
        if(chapterId!=null) {
            var chapter=learningChapterMapper.selectById(chapterId);
            if(chapter==null) throw new BusinessException(404,"章节不存在");
            if(pathId!=null && !pathId.equals(chapter.getPathId())) throw new BusinessException(400,"章节与路径不匹配");
            pathId=chapter.getPathId();
        }
        if(pathId!=null) {
            var path=learningPathMapper.selectById(pathId);
            if(path==null) throw new BusinessException(404,"学习路径不存在");
            if(!java.util.Objects.equals(path.getOwnerUserId(),0L) && !java.util.Objects.equals(path.getOwnerUserId(),userId)) throw new BusinessException(403,"无权访问该学习路径");
        }
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
        List<LearningFlashcard> cards = parseFlashcards(raw, doc, pathId, chapterId, userId);
        if (cards.isEmpty()) {
            throw new BusinessException("AI 未返回有效闪卡，请重试或调整文档内容");
        }
        for (LearningFlashcard card : cards) {
            flashcardMapper.insert(card);
        }
        return cards;
    }

    /**
     * 解析大模型返回的闪卡 JSON（兼容 ```json 围栏），构建闪卡实体列表。
     *
     * <p>P0-1b：构建时必须落 {@code userId}（由调用方从登录态透传）—— 否则
     * {@code learning_flashcard.user_id} 的 {@code NOT NULL} 约束会让整批插入失败。
     * 同时补齐 {@code docId} 与 {@code sourceType=AI_DOC}，与其它 AI 闪卡写入路径
     * （{@code LearningServiceImpl}）保持同一口径。</p>
     */
    private List<LearningFlashcard> parseFlashcards(String raw, DocDocument doc, Long pathId, Long chapterId, Long userId) {
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
                card.setUserId(userId);
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
                card.setDocId(doc.getId());
                card.setSourceType("AI_DOC");
                card.setReviewCount(0);
                card.setReviewInterval(0);
                result.add(card);
            }
        } catch (Exception e) {
            log.error("解析闪卡 JSON 失败: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public List<DocDocument> listByCategory(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        return this.list(access.scope(new LambdaQueryWrapper<DocDocument>(),access.actor())
                .eq(DocDocument::getCategoryId, categoryId)
                .eq(DocDocument::getStatus, 1)
                .orderByAsc(DocDocument::getSortOrder)
                .orderByDesc(DocDocument::getCreateTime));
    }

    private void requireEditableBatch(List<Long> ids,List<DocDocument> docs) {
        if(docs.size()!=new java.util.HashSet<>(ids).size()) throw new BusinessException(404,"文档不存在");
        docs.forEach(d->access.requireEdit(d,access.actor()));
    }
    /** Do not accept persistence/audit/download fields or counters from JSON entities. */
    private void sanitize(DocDocument doc,boolean create) {
        doc.setDeleted(null);doc.setCreateTime(null);doc.setUpdateTime(null);doc.setFileUrl(null);doc.setFileName(null);doc.setFileSize(null);
        doc.setSourcePath(null);doc.setContentHash(null);doc.setCategoryPath(null);
        doc.setViewCount(create?0:null);doc.setReadCount(create?0:null);doc.setFavoriteCount(create?0:null);
        if(create){doc.setId(null);if(doc.getStatus()==null)doc.setStatus(1);}
    }
}
