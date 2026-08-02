package com.knowflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.config.UploadConfigProperties;
import com.knowflow.dto.KnowledgeImportOptionsDTO;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.service.AiService;
import com.knowflow.service.CategoryService;
import com.knowflow.service.DocChunkService;
import com.knowflow.service.DocumentTextExtractor;
import com.knowflow.service.ImportCancelService;
import com.knowflow.service.ImportProgressListener;
import com.knowflow.service.KbMemberService;
import com.knowflow.service.KnowledgeImportService;
import com.knowflow.service.KnowledgeService;
import com.knowflow.vo.KnowledgeImportResultVO;
import com.knowflow.vo.KnowledgeImportResultVO.ItemLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识库目录批量导入服务实现。
 * <p>
 * 核心流程：
 * <ol>
 *     <li>解析上传文件列表，分离 markdown 文档与图片资源</li>
 *     <li>构建目录→分类映射缓存（按目录路径懒创建子分类，3 级深度限制）</li>
 *     <li>逐个处理 markdown 文件：读取正文→计算 hash→增量去重→迁移图片→重写链接→生成标签→入库</li>
 *     <li>收集逐条明细日志，返回完整导入结果</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeImportServiceImpl implements KnowledgeImportService {

    private final DocDocumentMapper docDocumentMapper;
    private final DocCategoryMapper docCategoryMapper;
    private final CategoryService categoryService;
    private final KbMemberService kbMemberService;
    private final AiService aiService;
    private final DocChunkService docChunkService;
    private final KnowledgeService knowledgeService;
    private final UploadConfigProperties uploadConfig;
    private final DocumentTextExtractor documentTextExtractor;
    private final ImportCancelService importCancelService;

    /**
     * 支持的文档扩展名。
     * <p>除 Markdown/纯文本外，新增 PDF/DOC/DOCX/PPT/PPTX/RTF/HTML 等富文档格式，
     * 这些二进制文档通过 {@link DocumentTextExtractor}（基于 Apache Tika）提取纯文本，
     * 避免直接按 UTF-8 解码二进制字节导致的乱码。
     * <p>同时支持常见代码文件（.java/.py/.vue/.js/.ts/.css/.xml/.yml 等），
     * 导入时自动包装为 Markdown 代码块（```lang\n代码\n```），
     * 详情页 Markdown 渲染器会按语言高亮显示。
     */
    private static final Set<String> DOC_EXTS = Set.of(
            "md", "markdown", "txt",
            "pdf", "doc", "docx", "ppt", "pptx", "rtf", "html", "htm",
            // 代码文件（导入时包装为 Markdown 代码块）
            "java", "py", "css", "vue", "js", "ts", "xml", "yml", "yaml",
            "json", "sql", "sh", "bash", "go", "rs", "c", "cpp", "h", "hpp",
            "kt", "swift", "rb", "php", "scss", "less", "toml", "ini", "conf",
            "jsx", "tsx", "dart"
    );

    /**
     * 二进制富文档扩展名集合（需走 Tika 解析）。
     * 用于在 {@link #readContent} 中区分文本/二进制读取路径。
     */
    private static final Set<String> BINARY_DOC_EXTS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "rtf"
    );

    /**
     * 代码文件扩展名集合（导入时包装为 Markdown 代码块以启用语法高亮）。
     * 与 {@link #DOC_EXTS} 中的代码部分保持一致，单独抽出用于 {@link #wrapCodeAsMarkdown} 判断。
     * <p>注意：{@code html}/{@code htm} 既在 {@link #DOC_EXTS} 中（作为可导入格式），
     * 也在本集合中（作为代码文件包装为代码块展示源码）；
     * 而 {@link #BINARY_DOC_EXTS} 中的 pdf/doc/docx 等走 Tika 提取纯文本，不在此集合。
     */
    private static final Set<String> CODE_EXTS = Set.of(
            "java", "py", "css", "vue", "js", "ts", "xml", "yml", "yaml",
            "json", "sql", "sh", "bash", "go", "rs", "c", "cpp", "h", "hpp",
            "kt", "swift", "rb", "php", "scss", "less", "toml", "ini", "conf",
            "jsx", "tsx", "dart", "html", "htm"
    );

    /**
     * 代码文件扩展名 → Markdown 代码块语言标识符映射。
     * 用于在 {@link #wrapCodeAsMarkdown} 中生成 ```lang 语法块。
     * 未配置的扩展名回退为空字符串（plain code block）。
     */
    private static final Map<String, String> EXT_TO_LANG;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("java", "java");       m.put("py", "python");
        m.put("css", "css");         m.put("vue", "vue");
        m.put("js", "javascript");   m.put("ts", "typescript");
        m.put("xml", "xml");         m.put("html", "html");
        m.put("htm", "html");        m.put("yml", "yaml");
        m.put("yaml", "yaml");       m.put("json", "json");
        m.put("sql", "sql");         m.put("sh", "bash");
        m.put("bash", "bash");       m.put("go", "go");
        m.put("rs", "rust");         m.put("c", "c");
        m.put("cpp", "cpp");         m.put("h", "c");
        m.put("hpp", "cpp");         m.put("kt", "kotlin");
        m.put("swift", "swift");     m.put("rb", "ruby");
        m.put("php", "php");         m.put("scss", "scss");
        m.put("less", "less");       m.put("toml", "toml");
        m.put("ini", "ini");         m.put("conf", "ini");
        m.put("jsx", "jsx");         m.put("tsx", "tsx");
        m.put("dart", "dart");
        EXT_TO_LANG = java.util.Collections.unmodifiableMap(m);
    }

    /** 图片扩展名集合 */
    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico"
    );
    /** 系统分类最大深度（顶级=1，子=2，孙=3） */
    private static final int MAX_CATEGORY_DEPTH = 3;
    /** 自动标签数量上限 */
    private static final int MAX_AUTO_TAGS = 8;
    /** 单篇正文关键词提取上限 */
    private static final int MAX_KEYWORDS = 5;

    /** Obsidian 图片嵌入语法：![[image.png]] 或 ![[image.png|400]] */
    private static final Pattern OBSIDIAN_IMAGE_PATTERN = Pattern.compile(
            "!\\[\\[([^\\]|\\n]+?)(?:\\|([^\\]|\\n]+?))?\\]\\]");
    /** 标准 Markdown 图片语法：![alt](path) */
    private static final Pattern MD_IMAGE_PATTERN = Pattern.compile(
            "!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    /** Markdown H1 标题 */
    private static final Pattern H1_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    /** front-matter 中的 tags 字段 */
    private static final Pattern FRONTMATTER_TAGS_PATTERN = Pattern.compile(
            "(?ms)^---\\n.*?tags:\\s*\\n((?:\\s+-\\s+.+\\n)+).*?^---\\n");
    /** front-matter 中的 tags 行内格式 tags: [a, b, c] */
    private static final Pattern FRONTMATTER_TAGS_INLINE_PATTERN = Pattern.compile(
            "(?ms)^---\\n.*?tags:\\s*\\[(.+?)\\].*?^---\\n");

    @Override
    public KnowledgeImportResultVO importDirectory(MultipartFile[] files,
                                                    KnowledgeImportOptionsDTO options,
                                                    Long userId) {
        // 委托给带进度的版本，不传监听器（退化为同步执行）
        return importDirectoryWithProgress(files, options, userId, null, null);
    }

    @Override
    public KnowledgeImportResultVO importDirectoryWithProgress(MultipartFile[] files,
                                                                KnowledgeImportOptionsDTO options,
                                                                Long userId,
                                                                String batchId,
                                                                ImportProgressListener listener) {
        if (files == null || files.length == 0) {
            throw new BusinessException(400, "未选择任何文件");
        }
        // 参数兜底
        if (options == null) {
            options = new KnowledgeImportOptionsDTO();
        }
        boolean createSubCats = !Boolean.FALSE.equals(options.getCreateSubCategories());
        boolean autoTags = !Boolean.FALSE.equals(options.getAutoTags());
        boolean aiTags = Boolean.TRUE.equals(options.getAiTags());
        boolean incremental = !Boolean.FALSE.equals(options.getIncremental());
        int maxChars = options.getMaxContentChars() != null && options.getMaxContentChars() > 0
                ? options.getMaxContentChars() : 50000;

        // 校验目标知识库权限
        Long targetCategoryId = options.getTargetCategoryId();
        if (targetCategoryId == null) {
            throw new BusinessException(400, "请选择目标知识库");
        }
        if (!kbMemberService.canEditDocs(targetCategoryId, userId)) {
            throw new BusinessException(403, "无权向该知识库导入文档（需 Owner 或 Editor 权限）");
        }

        // 生成导入批次 ID（用于图片存储子目录 + 取消控制）
        final String effectiveBatchId = (batchId == null || batchId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : batchId;

        KnowledgeImportResultVO result = new KnowledgeImportResultVO();
        result.setTargetCategoryId(targetCategoryId);

        // 1. 分离文档与图片，构建图片名→MultipartFile 映射
        List<MultipartFile> docFiles = new ArrayList<>();
        Map<String, MultipartFile> imageMap = new HashMap<>();
        for (MultipartFile f : files) {
            String relPath = f.getOriginalFilename();
            if (StrUtil.isBlank(relPath)) continue;
            String ext = getExtension(relPath);
            if (IMAGE_EXTS.contains(ext)) {
                // 图片以「文件名」为 key（Obsidian 引用图片时不带路径）
                String imageName = getFileName(relPath);
                imageMap.putIfAbsent(imageName, f);
                imageMap.putIfAbsent(relPath, f);
            } else if (DOC_EXTS.contains(ext)) {
                docFiles.add(f);
            }
            // 其他类型文件暂不处理
        }
        int totalDocs = docFiles.size();
        result.setTotalDocs(totalDocs);

        // 通知开始
        safeCallback(() -> listener.onStart(effectiveBatchId, totalDocs), listener);

        // 2. 目录→分类映射缓存（避免同一目录重复查库/重复创建）
        Map<String, Long> dirCategoryCache = new HashMap<>();
        dirCategoryCache.put("", targetCategoryId); // 根目录 = 目标知识库
        Set<String> createdCategoryNames = new LinkedHashSet<>();

        // 3. 逐个处理文档（带进度回调 + 取消检查）
        boolean cancelled = false;
        for (int i = 0; i < docFiles.size(); i++) {
            MultipartFile docFile = docFiles.get(i);
            String relPath = docFile.getOriginalFilename();
            int index = i + 1;

            // 取消检查
            if (importCancelService.isCancelled(effectiveBatchId)) {
                cancelled = true;
                log.info("导入已取消: batchId={}, 已处理 {}/{}", effectiveBatchId, i, totalDocs);
                safeCallback(() -> listener.onCancel("用户取消导入"), listener);
                break;
            }

            // 通知单文件开始
            final int idx = index;
            final int total = totalDocs;
            final String path = relPath;
            safeCallback(() -> listener.onFileStart(idx, total, path), listener);

            String status;
            String message;
            // 记录处理前的计数，用于推断本文件状态
            int prevSuccess = result.getSuccessCount();
            int prevSkipped = result.getSkippedCount();
            int prevFailed = result.getFailedCount();
            try {
                processOneDoc(docFile, relPath, options, userId, effectiveBatchId,
                        createSubCats, autoTags, aiTags, incremental, maxChars,
                        targetCategoryId, imageMap, dirCategoryCache, createdCategoryNames, result);
                // processOneDoc 内部会更新 success/skipped/failed 计数，根据增量判断状态
                if (result.getSkippedCount() > prevSkipped) {
                    status = "skipped";
                    message = "文件未变更，增量跳过";
                } else if (result.getFailedCount() > prevFailed) {
                    status = "failed";
                    message = "文档内容为空或解析失败";
                } else if (result.getSuccessCount() > prevSuccess) {
                    status = "success";
                    message = "导入成功";
                } else {
                    status = "success";
                    message = "处理完成";
                }
            } catch (Exception e) {
                log.warn("导入文档失败: path={}, error={}", relPath, e.getMessage());
                result.setFailedCount(result.getFailedCount() + 1);
                result.getFailedItems().add(ItemLog.of(
                        relPath, extractTitleFromPath(relPath), null, null,
                        "导入失败：" + e.getMessage()));
                status = "failed";
                message = "导入失败：" + e.getMessage();
            }

            // 通知单文件完成
            final String doneStatus = status;
            final String doneMsg = message;
            safeCallback(() -> listener.onFileDone(idx, total, path, doneStatus, doneMsg), listener);
        }

        // 4. 汇总新建分类
        result.setCreatedCategories(new ArrayList<>(createdCategoryNames));
        log.info("目录导入{}: batchId={}, total={}, success={}, skipped={}, failed={}, images={}",
                cancelled ? "已取消" : "完成", effectiveBatchId,
                result.getTotalDocs(), result.getSuccessCount(), result.getSkippedCount(),
                result.getFailedCount(), result.getImageCount());

        // 清理取消标志
        importCancelService.cleanup(effectiveBatchId);

        // 通知完成（取消时不重复回调 onComplete）
        if (!cancelled) {
            safeCallback(() -> listener.onComplete(result), listener);
        }
        return result;
    }

    /**
     * 安全回调：best-effort，回调失败不影响主流程。
     */
    private void safeCallback(Runnable action, ImportProgressListener listener) {
        if (listener == null) return;
        try {
            action.run();
        } catch (Exception e) {
            log.warn("进度回调失败: {}", e.getMessage());
        }
    }

    /**
     * 处理单个文档：读取→去重→图片迁移→链接重写→打标→入库。
     */
    private void processOneDoc(MultipartFile docFile, String relPath,
                                KnowledgeImportOptionsDTO options, Long userId, String batchId,
                                boolean createSubCats, boolean autoTags, boolean aiTags,
                                boolean incremental, int maxChars,
                                Long targetCategoryId,
                                Map<String, MultipartFile> imageMap,
                                Map<String, Long> dirCategoryCache,
                                Set<String> createdCategoryNames,
                                KnowledgeImportResultVO result) throws IOException {
        // 读取正文
        String content = readContent(docFile);
        if (StrUtil.isBlank(content)) {
            result.setFailedCount(result.getFailedCount() + 1);
            result.getFailedItems().add(ItemLog.of(relPath, extractTitleFromPath(relPath),
                    null, null, "文档内容为空"));
            return;
        }

        // 计算 content hash
        String contentHash = sha256(content);

        // 增量去重检查
        if (incremental) {
            DocDocument existing = docDocumentMapper.selectOne(new LambdaQueryWrapper<DocDocument>()
                    .eq(DocDocument::getSourcePath, relPath)
                    .eq(DocDocument::getContentHash, contentHash)
                    .last("LIMIT 1"));
            if (existing != null) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                DocCategory cat = categoryService.getById(existing.getCategoryId());
                String catName = cat != null ? cat.getName() : "";
                result.getSkippedItems().add(ItemLog.of(
                        relPath, existing.getTitle(), existing.getCategoryId(), catName,
                        "文件未变更，增量跳过"));
                return;
            }
        }

        // 确定目标分类
        String dirPath = getDirPath(relPath);
        Long categoryId = createSubCats
                ? resolveOrCreateCategory(dirPath, targetCategoryId, userId, dirCategoryCache, createdCategoryNames)
                : targetCategoryId;
        DocCategory category = categoryService.getById(categoryId);
        String categoryName = category != null ? category.getName() : "";

        // 图片迁移 + 链接重写
        ImageRewriteResult rewriteResult = migrateAndRewriteImages(
                content, relPath, batchId, imageMap);
        content = rewriteResult.content;
        result.setImageCount(result.getImageCount() + rewriteResult.migratedCount);

        // 截断过长正文
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars) + "\n\n...(正文已截断)";
        }

        // 提取标题
        String title = extractTitle(content, relPath);

        // 生成标签
        String tags = autoTags
                ? generateTags(content, relPath, dirPath, aiTags, userId)
                : extractFrontMatterTags(content);

        // 提取摘要（取正文前 200 字，去除 markdown 标记）
        String summary = extractSummary(content, 200);

        // 构建文档实体
        DocDocument doc = new DocDocument();
        doc.setTitle(title);
        doc.setContent(content);
        doc.setSummary(summary);
        doc.setTags(tags);
        doc.setCategoryId(categoryId);
        doc.setCategoryPath(categoryId.toString());
        doc.setSourcePath(relPath);
        doc.setContentHash(contentHash);
        doc.setFileName(getFileName(relPath));
        doc.setWordCount(content.length());
        doc.setStatus(1);
        doc.setDifficulty(1);
        doc.setSortOrder(0);

        docDocumentMapper.insert(doc);

        // 增加分类文档数
        categoryService.incrementDocCount(categoryId);

        // RAG 分块索引（best-effort）
        try {
            docChunkService.indexDocument(doc.getId(), doc.getContent());
        } catch (Exception e) {
            log.warn("文档分块索引失败（不影响导入）: docId={}, {}", doc.getId(), e.getMessage());
        }
        // 知识图谱抽取（best-effort）
        try {
            knowledgeService.extractDoc(doc.getId());
        } catch (Exception e) {
            log.warn("文档实体关系抽取失败（不影响导入）: docId={}, {}", doc.getId(), e.getMessage());
        }

        result.setSuccessCount(result.getSuccessCount() + 1);
        result.getSuccessItems().add(ItemLog.of(
                relPath, title, categoryId, categoryName, "导入成功"));
    }

    // ==================== 图片迁移与链接重写 ====================

    /**
     * 迁移文档引用的图片到服务器上传目录，并重写 Markdown 中的图片链接。
     * <p>
     * 支持 Obsidian 语法（![[image.png]] / ![[image.png|400]]）和标准 Markdown 语法（![alt](path)）。
     * 图片优先从 imageMap 中按文件名查找，找到则迁移并重写链接，未找到则保留原链接。
     */
    private ImageRewriteResult migrateAndRewriteImages(String content, String docRelPath,
                                                        String batchId,
                                                        Map<String, MultipartFile> imageMap) {
        ImageRewriteResult result = new ImageRewriteResult();
        // 兜底初始化 content，避免无图片时返回 null 导致后续 NPE
        result.content = content;
        if (imageMap.isEmpty()) {
            return result;
        }

        String docDir = getDirPath(docRelPath);

        // 1. 处理 Obsidian 图片嵌入语法 ![[image.png]] 或 ![[image.png|400]]
        Matcher obsMatcher = OBSIDIAN_IMAGE_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (obsMatcher.find()) {
            String imageName = obsMatcher.group(1).trim();
            String widthParam = obsMatcher.group(2);
            MultipartFile imageFile = resolveImage(imageName, docDir, imageMap);
            if (imageFile != null) {
                String newUrl = saveImage(imageFile, batchId);
                if (newUrl != null) {
                    result.migratedCount++;
                    String alt = imageName;
                    String replacement = widthParam != null
                            ? "![" + alt + "](" + newUrl + " \"" + widthParam + "\")"
                            : "![" + alt + "](" + newUrl + ")";
                    obsMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                    continue;
                }
            }
            // 图片未找到，保留原文
            obsMatcher.appendReplacement(sb, Matcher.quoteReplacement(obsMatcher.group()));
        }
        obsMatcher.appendTail(sb);
        content = sb.toString();

        // 2. 处理标准 Markdown 图片语法 ![alt](path)
        Matcher mdMatcher = MD_IMAGE_PATTERN.matcher(content);
        sb = new StringBuffer();
        while (mdMatcher.find()) {
            String alt = mdMatcher.group(1);
            String imgPath = mdMatcher.group(2).trim();
            // 跳过已经是 HTTP URL 的图片
            if (imgPath.startsWith("http://") || imgPath.startsWith("https://")) {
                mdMatcher.appendReplacement(sb, Matcher.quoteReplacement(mdMatcher.group()));
                continue;
            }
            // 提取图片文件名（可能带相对路径）
            String imageName = getFileName(imgPath);
            MultipartFile imageFile = resolveImage(imageName, docDir, imageMap);
            if (imageFile != null) {
                String newUrl = saveImage(imageFile, batchId);
                if (newUrl != null) {
                    result.migratedCount++;
                    mdMatcher.appendReplacement(sb,
                            Matcher.quoteReplacement("![" + alt + "](" + newUrl + ")"));
                    continue;
                }
            }
            mdMatcher.appendReplacement(sb, Matcher.quoteReplacement(mdMatcher.group()));
        }
        mdMatcher.appendTail(sb);
        result.content = sb.toString();

        return result;
    }

    /**
     * 按文件名 + 文档所在目录查找图片文件。
     * Obsidian 中图片可能在同级目录、上级 image 目录或根目录。
     */
    private MultipartFile resolveImage(String imageName, String docDir,
                                        Map<String, MultipartFile> imageMap) {
        // 1. 直接按文件名查找
        MultipartFile f = imageMap.get(imageName);
        if (f != null) return f;
        // 2. 按文档同级目录 + 文件名查找
        if (StrUtil.isNotBlank(docDir)) {
            f = imageMap.get(docDir + "/" + imageName);
            if (f != null) return f;
        }
        // 3. 按常见图片目录查找（image/ images/ attachments/）
        for (String imgDir : new String[]{"image", "images", "attachments", "assets"}) {
            f = imageMap.get(imgDir + "/" + imageName);
            if (f != null) return f;
        }
        return null;
    }

    /**
     * 保存图片到服务器上传目录，返回可访问的 URL 路径。
     */
    private String saveImage(MultipartFile imageFile, String batchId) {
        try {
            String originalName = imageFile.getOriginalFilename();
            String imageName = getFileName(originalName != null ? originalName : "image.png");
            // 生成唯一文件名避免冲突
            String uniqueName = batchId + "_" + System.nanoTime() + "_" + sanitizeFileName(imageName);
            String subDir = "imports/" + batchId;
            Path dirPath = Paths.get(uploadConfig.getDir(), subDir);
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(uniqueName);
            imageFile.transferTo(filePath.toFile());
            return "/uploads/" + subDir + "/" + uniqueName;
        } catch (IOException e) {
            log.warn("图片保存失败: {}", imageFile.getOriginalFilename(), e);
            return null;
        }
    }

    // ==================== 分类映射 ====================

    /**
     * 根据目录路径解析或创建对应的知识库分类。
     * <p>
     * 系统分类最多 3 级深度（顶级知识库→子分类→孙分类）。
     * 若目录层级超出 3 级，超出部分不创建分类，文档挂在最深一级分类下。
     */
    private Long resolveOrCreateCategory(String dirPath, Long targetCategoryId, Long userId,
                                          Map<String, Long> cache, Set<String> createdNames) {
        if (StrUtil.isBlank(dirPath)) {
            return targetCategoryId;
        }
        // 命中缓存
        Long cached = cache.get(dirPath);
        if (cached != null) return cached;

        String[] parts = dirPath.split("/");
        Long currentParentId = targetCategoryId;
        StringBuilder currentPath = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String dirName = parts[i].trim();
            if (StrUtil.isBlank(dirName)) continue;
            if (currentPath.length() > 0) currentPath.append("/");
            currentPath.append(dirName);
            String pathKey = currentPath.toString();

            // 命中缓存
            Long existing = cache.get(pathKey);
            if (existing != null) {
                currentParentId = existing;
                continue;
            }

            // 深度限制：targetCategoryId 是第 1 级，子分类第 2 级，孙分类第 3 级
            // i=0 时创建的是 targetCategoryId 的子分类（第 2 级），i=1 时第 3 级
            // i>=2 时不再创建分类，直接用 currentParentId
            if (i >= MAX_CATEGORY_DEPTH - 1) {
                cache.put(pathKey, currentParentId);
                continue;
            }

            // 查找是否已存在同名子分类
            DocCategory existingCat = docCategoryMapper.selectOne(new LambdaQueryWrapper<DocCategory>()
                    .eq(DocCategory::getName, dirName)
                    .eq(DocCategory::getParentId, currentParentId)
                    .eq(DocCategory::getStatus, 1)
                    .last("LIMIT 1"));
            if (existingCat != null) {
                currentParentId = existingCat.getId();
                cache.put(pathKey, currentParentId);
                continue;
            }

            // 创建新分类
            DocCategory newCat = new DocCategory();
            newCat.setName(dirName);
            newCat.setParentId(currentParentId);
            newCat.setStatus(1);
            newCat.setSortOrder(0);
            newCat.setDocCount(0);
            newCat.setOwnerId(userId);
            docCategoryMapper.insert(newCat);
            currentParentId = newCat.getId();
            cache.put(pathKey, currentParentId);
            createdNames.add(dirName);
        }

        cache.put(dirPath, currentParentId);
        return currentParentId;
    }

    // ==================== 标签生成 ====================

    /**
     * 自动生成标签：目录路径 + 文件名 + front-matter tags + 正文关键词，可选 AI 智能打标。
     */
    private String generateTags(String content, String relPath, String dirPath,
                                 boolean aiTags, Long userId) {
        Set<String> tags = new LinkedHashSet<>();

        // 1. front-matter 中的 tags
        tags.addAll(extractFrontMatterTagsList(content));

        // 2. 目录路径片段作为标签
        if (StrUtil.isNotBlank(dirPath)) {
            for (String part : dirPath.split("/")) {
                String t = part.trim();
                if (StrUtil.isNotBlank(t) && t.length() <= 20) {
                    tags.add(t);
                }
            }
        }

        // 3. 文件名作为标签（去除扩展名）
        String fileName = getFileName(relPath);
        String nameWithoutExt = stripExtension(fileName);
        if (StrUtil.isNotBlank(nameWithoutExt) && nameWithoutExt.length() <= 30) {
            tags.add(nameWithoutExt);
        }

        // 4. 正文关键词提取（简单频率统计）
        tags.addAll(extractKeywords(content, MAX_KEYWORDS));

        // 5. AI 智能打标（可选）
        if (aiTags) {
            try {
                List<String> aiTagsList = generateAiTags(content, userId);
                tags.addAll(aiTagsList);
            } catch (Exception e) {
                log.warn("AI 打标失败（不影响导入）: {}", e.getMessage());
            }
        }

        // 限制标签数量并拼接
        List<String> result = tags.stream()
                .filter(t -> StrUtil.isNotBlank(t))
                .limit(MAX_AUTO_TAGS)
                .collect(Collectors.toList());
        return String.join(",", result);
    }

    /**
     * 调用 AI 生成标签（基于文档正文）。
     */
    private List<String> generateAiTags(String content, Long userId) {
        if (!aiService.isConfigured()) {
            return List.of();
        }
        // 截取前 3000 字发给 AI，避免 token 超限
        String excerpt = content.length() > 3000 ? content.substring(0, 3000) : content;
        String systemPrompt = "你是知识管理专家。根据给定的文档内容，生成 3-5 个精准的中文标签。"
                + "只返回标签文本，用逗号分隔，不要有其他内容。例如：机器学习,神经网络,深度学习";
        try {
            String response = aiService.complete(systemPrompt, excerpt, null, userId);
            if (StrUtil.isNotBlank(response)) {
                return Arrays.stream(response.split("[,，\\n]"))
                        .map(String::trim)
                        .filter(s -> StrUtil.isNotBlank(s) && s.length() <= 20)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("AI 标签生成异常: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 简单关键词提取：去除常见停用词后按词频取 Top N。
     */
    private List<String> extractKeywords(String content, int topN) {
        // 去除 markdown 标记、代码块
        String text = content.replaceAll("(?s)```.*?```", " ")
                .replaceAll("(?s)`[^`]+`", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]+\\)", " ")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("[#*>|\\[\\](){}_~=-]", " ")
                .replaceAll("\\s+", " ");
        // 中文按 2-4 字提取，英文按单词
        Map<String, Integer> freq = new LinkedHashMap<>();
        // 英文单词
        for (String word : text.split("[\\s,.;:!?，。；：！？、（）()\"'<>]+")) {
            String w = word.trim().toLowerCase(Locale.ROOT);
            if (w.length() >= 3 && w.length() <= 20 && !isStopWord(w)) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "her", "was",
            "one", "our", "out", "has", "have", "from", "this", "that", "with", "your",
            "will", "they", "been", "more", "than", "them", "then", "some", "what", "each",
            "which", "their", "there", "would", "about", "after", "also", "make",
            "like", "into", "only", "other", "over", "such", "these", "just", "when",
            "where", "while", "using", "use", "used", "get", "set", "via"
    );

    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }

    // ==================== front-matter 处理 ====================

    /**
     * 从 front-matter 提取 tags（列表格式或行内格式），返回逗号分隔字符串。
     */
    private String extractFrontMatterTags(String content) {
        List<String> tags = extractFrontMatterTagsList(content);
        return String.join(",", tags);
    }

    private List<String> extractFrontMatterTagsList(String content) {
        List<String> tags = new ArrayList<>();
        if (content == null || !content.startsWith("---\n")) {
            return tags;
        }
        // 列表格式：tags:\n  - tag1\n  - tag2
        Matcher m1 = FRONTMATTER_TAGS_PATTERN.matcher(content);
        if (m1.find()) {
            String block = m1.group(1);
            for (String line : block.split("\n")) {
                String t = line.replaceAll("^\\s+-\\s+", "").trim();
                if (StrUtil.isNotBlank(t)) {
                    tags.add(t.replaceAll("^['\"]|['\"]$", ""));
                }
            }
            return tags;
        }
        // 行内格式：tags: [tag1, tag2]
        Matcher m2 = FRONTMATTER_TAGS_INLINE_PATTERN.matcher(content);
        if (m2.find()) {
            String raw = m2.group(1);
            for (String t : raw.split(",")) {
                String tag = t.trim().replaceAll("^['\"]|['\"]$", "");
                if (StrUtil.isNotBlank(tag)) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    /**
     * 去除 front-matter 后的正文（用于标题提取和摘要）。
     */
    private String stripFrontMatter(String content) {
        if (content != null && content.startsWith("---\n")) {
            int end = content.indexOf("\n---\n", 4);
            if (end > 0) {
                return content.substring(end + 5);
            }
        }
        return content;
    }

    // ==================== 标题与摘要 ====================

    /**
     * 提取文档标题：优先 H1 → front-matter title → 文件名。
     */
    private String extractTitle(String content, String relPath) {
        String body = stripFrontMatter(content);
        Matcher m = H1_PATTERN.matcher(body);
        if (m.find()) {
            return m.group(1).trim();
        }
        // front-matter title
        if (content != null && content.startsWith("---\n")) {
            Pattern titlePattern = Pattern.compile("(?m)^title:\\s*(.+)$");
            Matcher tm = titlePattern.matcher(content.substring(0, Math.min(content.length(), 500)));
            if (tm.find()) {
                return tm.group(1).trim().replaceAll("^['\"]|['\"]$", "");
            }
        }
        return extractTitleFromPath(relPath);
    }

    /**
     * 从文件路径提取标题（去除扩展名）。
     */
    private String extractTitleFromPath(String relPath) {
        String name = getFileName(relPath);
        return stripExtension(name);
    }

    /**
     * 提取摘要：去除 Markdown 标记后取前 N 字。
     */
    private String extractSummary(String content, int maxLen) {
        String body = stripFrontMatter(content);
        String text = body.replaceAll("(?s)```.*?```", " ")
                .replaceAll("(?s)`[^`]+`", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]+\\)", " ")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("[#*>|\\[\\](){}_~=-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() > maxLen) {
            return text.substring(0, maxLen) + "...";
        }
        return text;
    }

    // ==================== 工具方法 ====================

    /**
     * 读取文件文本内容。
     * <p>按扩展名分流：
     * <ul>
     *   <li>二进制富文档（pdf/doc/docx/ppt/pptx/rtf）：调用 {@link DocumentTextExtractor}
     *       基于 Apache Tika 提取纯文本，避免按 UTF-8 解码二进制字节导致乱码</li>
     *   <li>文本类（md/markdown/txt/html/htm）：直接 UTF-8 解码</li>
     * </ul>
     * Tika 内部会自动探测 MIME、解析编码、清理控制字符，兼容中英文及特殊字符。
     * 解析失败（损坏/加密/不支持）兜底返回空串，由上层判空逻辑标记为失败项。
     */
    private String readContent(MultipartFile file) throws IOException {
        String ext = getExtension(file.getOriginalFilename());
        if (BINARY_DOC_EXTS.contains(ext)) {
            // 二进制富文档：走 Tika 提取，带超时与 OOM 防护
            String text = documentTextExtractor.extractText(file);
            if (text == null) {
                log.warn("文档文本提取返回 null（可能损坏或加密）：{}", file.getOriginalFilename());
                return "";
            }
            return text;
        }
        // 纯文本类：UTF-8 解码
        byte[] bytes = file.getBytes();
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        // 代码文件：包装为 Markdown 代码块，详情页渲染时自动启用语法高亮
        if (CODE_EXTS.contains(ext)) {
            return wrapCodeAsMarkdown(content, ext);
        }
        return content;
    }

    /**
     * 将代码文件内容包装为 Markdown 代码块。
     * <p>包装格式：
     * <pre>
     * ```lang
     * 原始代码内容
     * ```
     * </pre>
     * 这样文档详情页的 Markdown 渲染器（如 markdown-it + highlight.js）会
     * 自动识别语言并应用对应语法高亮，避免代码以纯文本方式展示。
     *
     * @param code 原始代码内容
     * @param ext  文件扩展名（小写，不含点）
     * @return 包装后的 Markdown 文本
     */
    private String wrapCodeAsMarkdown(String code, String ext) {
        String lang = EXT_TO_LANG.getOrDefault(ext, "");
        StringBuilder sb = new StringBuilder(code.length() + 16);
        sb.append("```").append(lang).append('\n');
        sb.append(code);
        // 确保代码末尾有换行，避免 ``` 与代码同行导致渲染异常
        if (code.isEmpty() || code.charAt(code.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append("```");
        return sb.toString();
    }

    /**
     * 计算 SHA-256 哈希（十六进制字符串）。
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取文件扩展名（小写，不含点） */
    private String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 获取文件名（含扩展名） */
    private String getFileName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** 去除文件扩展名 */
    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 获取目录路径（相对路径中除文件名外的部分） */
    private String getDirPath(String relPath) {
        int slash = relPath.lastIndexOf('/');
        return slash > 0 ? relPath.substring(0, slash) : "";
    }

    /** 清理文件名中的特殊字符 */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** 图片迁移与链接重写的中间结果 */
    private static class ImageRewriteResult {
        String content;
        int migratedCount;
    }
}
