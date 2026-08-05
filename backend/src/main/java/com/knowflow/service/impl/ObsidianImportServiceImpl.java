package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.ObsidianImportDTO;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.ImportProgressListener;
import com.knowflow.service.KnowledgeImportService;
import com.knowflow.service.ObsidianImportService;
import com.knowflow.service.PathImportService;
import com.knowflow.util.LocalFileMultipartFile;
import com.knowflow.vo.KnowledgeImportResultVO;
import com.knowflow.vo.ObsidianImportResultVO;
import com.knowflow.vo.PathImportScanVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

/**
 * Obsidian 目录一键导入服务实现。
 * <p>编排流程：扫描目录 → 导入知识库（含图片兜底迁移）→ 按需生成学习路径 / 闪卡 / 题库。
 * 内容提炼采用规则模板（基于 Markdown 标题层级），环境无关、可离线，不依赖 AI 服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObsidianImportServiceImpl implements ObsidianImportService {

    private final PathImportService pathImportService;
    private final KnowledgeImportService knowledgeImportService;
    private final DocDocumentMapper docDocumentMapper;
    private final DocCategoryMapper docCategoryMapper;
    private final LearningPathMapper learningPathMapper;
    private final LearningChapterMapper learningChapterMapper;
    private final LearningFlashcardMapper learningFlashcardMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final ImportTemplateMapper importTemplateMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 上传根目录（与 WebMvcConfig 的 /uploads 映射一致）。 */
    private static final Pattern OBS_IMG = Pattern.compile("!\\[\\[([^\\]|\\n]+?)(?:\\|([^\\]|\\n]+?))?\\]\\]");
    private static final Set<String> IMG_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico");
    private static final String SRC_TAG = "obsidian-import";
    /** 每篇文档最多生成的闪卡数（避免规则模板数量爆炸）。 */
    private static final int MAX_FLASHCARD_PER_DOC = 10;
    /** 每篇文档最多生成的题库题数（简答+判断合计）。 */
    private static final int MAX_QUIZ_PER_DOC = 8;

    @Override
    public ObsidianImportResultVO importAll(ObsidianImportDTO dto, Long userId) {
        // 兼容入口：同步执行，无进度推送
        return doImport(dto, userId, UUID().substring(0, 12), null);
    }

    @Override
    public PathImportScanVO scanFiles(List<String> filePaths) {
        PathImportScanVO vo = new PathImportScanVO();
        vo.setRootName("文件选择");
        vo.setFile(true);
        vo.setAbsolutePath("");
        List<PathImportScanVO.FileEntry> entries = new ArrayList<>();
        int doc = 0, img = 0;
        for (String fp : filePaths) {
            File f = new File(pathImportService.resolvePath(fp, null));
            if (!f.exists() || !f.canRead()) continue;
            String extension = ext(f.getName());
            if (extension.isEmpty()) continue;
            PathImportScanVO.FileEntry e = new PathImportScanVO.FileEntry();
            e.setName(f.getName());
            e.setPath(f.getName());
            boolean isImg = IMG_EXT.contains(extension);
            e.setType(isImg ? "image" : "doc");
            e.setExt(extension);
            e.setSize(f.length());
            entries.add(e);
            if (isImg) img++; else doc++;
        }
        entries.sort(Comparator.comparing(PathImportScanVO.FileEntry::getPath));
        vo.setFiles(entries);
        vo.setDocCount(doc);
        vo.setImageCount(img);
        vo.setDirCount(0);
        return vo;
    }

    @Override
    public ObsidianImportResultVO importAllWithProgress(ObsidianImportDTO dto, Long userId,
                                                        String batchId, ImportProgressListener listener) {
        return doImport(dto, userId, batchId, listener);
    }

    /**
     * 统一的导入编排（目录模式 / 文件选择模式共用）。
     * <ul>
     *   <li>知识库导入走 KnowledgeImportService.importDirectoryWithProgress，由 listener 推送文件级进度</li>
     *   <li>闪卡 / 题库生成采用线程池并行解析，主线程批量持久化，提升导入速度</li>
     *   <li>listener 为 null 时退化为同步执行</li>
     * </ul>
     */
    private ObsidianImportResultVO doImport(ObsidianImportDTO dto, Long userId, String batchId,
                                           ImportProgressListener listener) {
        ObsidianImportResultVO result = new ObsidianImportResultVO();
        result.setGeneratedModules(new ArrayList<>());

        boolean fileMode = dto.getFilePaths() != null && !dto.getFilePaths().isEmpty();
        String absPath = "";
        PathImportScanVO scan = new PathImportScanVO();
        MultipartFile[] files;
        List<String> mdPaths = new ArrayList<>();
        Map<String, List<String>> dirDocs = new LinkedHashMap<>();

        if (listener != null) {
            listener.onStart(batchId, 0);
        }

        if (fileMode) {
            // 文件选择模式：仅导入用户指定的单个 / 多个文件，不再递归整目录
            List<MultipartFile> list = new ArrayList<>();
            for (String fp : dto.getFilePaths()) {
                String resolved = pathImportService.resolvePath(fp, dto.getRelativeTo());
                File f = new File(resolved);
                if (!f.exists() || !f.canRead()) {
                    if (listener != null) listener.onFileDone(0, 0, fp, "skipped", "文件不存在或不可读");
                    continue;
                }
                String extension = ext(f.getName());
                if (extension.isEmpty()) continue;
                String rel = f.getName(); // 单文件模式：相对路径即文件名
                list.add(new LocalFileMultipartFile(f, rel));
                if ("md".equals(extension)) {
                    mdPaths.add(rel);
                    dirDocs.computeIfAbsent("", k -> new ArrayList<>()).add(rel);
                } else if (IMG_EXT.contains(extension)) {
                    // 图片文件随知识库一并导入
                }
            }
            files = list.toArray(new MultipartFile[0]);
            scan.setRootName(fileMode ? "文件导入" : "");
            scan.setFile(files.length <= 1);
            scan.setDocCount(mdPaths.size());
            scan.setImageCount(files.length - mdPaths.size());
            if (files.length == 0) {
                if (listener != null) listener.onError("未选中任何可导入的文件");
                throw new BusinessException("未选中任何可导入的文件");
            }
        } else {
            // 1. 解析并扫描路径（relativeTo 支持相对路径基准）
            absPath = pathImportService.resolvePath(dto.getPath(), dto.getRelativeTo());
            result.setAbsolutePath(absPath);
            scan = pathImportService.scanForImport(absPath);
            for (PathImportScanVO.FileEntry e : scan.getFiles()) {
                if ("doc".equals(e.getType())) {
                    mdPaths.add(e.getPath());
                    String dir = dirOf(e.getPath());
                    dirDocs.computeIfAbsent(dir, k -> new ArrayList<>()).add(e.getPath());
                }
            }
            // Obsidian 图片语法预处理：复制为临时目录并平铺图片、改写为标准 Markdown
            try {
                String batch = UUID().substring(0, 12);
                String tempDir = prepareTempDir(absPath, batch);
                files = pathImportService.collectFiles(tempDir);
            } catch (Exception e) {
                log.error("目录预处理异常: {}", e.getMessage(), e);
                if (listener != null) listener.onError("目录预处理失败：" + e.getMessage());
                throw new RuntimeException("目录预处理失败：" + e.getMessage());
            }
        }

        // 2. 确定目标知识库（复用以存在或新建）
        Long categoryId = dto.getTargetCategoryId();
        String categoryName;
        if (categoryId == null) {
            DocCategory cat = new DocCategory();
            cat.setName(fileMode ? "文件导入" : scan.getRootName());
            cat.setParentId(0L);
            cat.setStatus(1);
            cat.setSortOrder(10);
            docCategoryMapper.insert(cat);
            categoryId = cat.getId();
            categoryName = cat.getName();
        } else {
            DocCategory cat = docCategoryMapper.selectById(categoryId);
            categoryName = cat != null ? cat.getName() : "";
        }
        result.setCategoryId(categoryId);
        result.setCategoryName(categoryName);

        // 3. 导入知识库（带进度 / 兼容无进度）
        if (dto.getModules() == null || dto.getModules().contains("knowledge")) {
            try {
                KnowledgeImportResultVO ki = knowledgeImportService.importDirectoryWithProgress(
                        files, buildOptions(dto, categoryId), userId, batchId, listener);
                result.setDocCount(ki != null ? ki.getSuccessCount() : 0);
                result.getGeneratedModules().add("knowledge");
            } catch (Exception e) {
                log.error("知识库导入异常: {}", e.getMessage(), e);
                if (listener != null) listener.onError("知识库导入失败：" + e.getMessage());
                throw new RuntimeException("知识库导入失败：" + e.getMessage());
            }
        }

        // 4. 收集导入的文档（按子分类分组）。直接按分类树查询，避免依赖 sourcePath 精确匹配。
        List<Long> descendantCats = descendantCategoryIds(categoryId);
        Map<Long, List<Long>> catToDocIds = new HashMap<>();
        List<Long> allDocIds = new ArrayList<>();
        if (!descendantCats.isEmpty()) {
            List<DocDocument> all = docDocumentMapper.selectList(
                    new LambdaQueryWrapper<DocDocument>().in(DocDocument::getCategoryId, descendantCats));
            for (DocDocument d : all) {
                catToDocIds.computeIfAbsent(d.getCategoryId(), k -> new ArrayList<>()).add(d.getId());
                allDocIds.add(d.getId());
            }
        }
        // 目录名 → 子分类 ID 映射（用于章节挂文档）
        Map<String, Long> dirToCatId = new HashMap<>();
        dirToCatId.put("", categoryId);
        for (Long cid : descendantCats) {
            if (cid.equals(categoryId)) continue;
            DocCategory c = docCategoryMapper.selectById(cid);
            if (c != null) dirToCatId.put(c.getName(), cid);
        }
        if (result.getDocCount() <= 0) {
            result.setDocCount(allDocIds.size());
        }

        // 5. 按需生成其余模块
        boolean doPath = dto.getModules() != null && dto.getModules().contains("path");
        boolean doFlash = dto.getModules() != null && dto.getModules().contains("flashcard");
        boolean doQuiz = dto.getModules() != null && dto.getModules().contains("quiz");

        TemplateRule flashRule = resolveTemplate(dto.getFlashcardTemplateId(), "FLASHCARD");
        TemplateRule quizRule = resolveTemplate(dto.getQuizTemplateId(), "QUIZ");

        Map<String, List<Long>> dirFlashcardIds = new HashMap<>();
        if (doPath || doFlash || doQuiz) {
            List<DocDocument> docs = new ArrayList<>();
            for (Long id : allDocIds) {
                DocDocument d = docDocumentMapper.selectById(id);
                if (d != null) docs.add(d);
            }

            // 闪卡：线程池并行解析生成，主线程汇总批量持久化
            if (doFlash) {
                List<LearningFlashcard> fcEntities = parallelGenerate(docs, doc -> generateFlashcardEntities(doc, userId, flashRule));
                saveBatch(learningFlashcardMapper, fcEntities);
                int fc = fcEntities.size();
                // 按目录归集闪卡 ID，供章节挂载
                for (LearningFlashcard card : fcEntities) {
                    String dir = dirOfByCat(card.getCategoryId(), dirToCatId, categoryId);
                    dirFlashcardIds.computeIfAbsent(dir, k -> new ArrayList<>()).add(card.getId());
                }
                // 进度推送：逐条上报已生成的闪卡
                if (listener != null) {
                    int total = fcEntities.size();
                    for (int i = 0; i < fcEntities.size(); i++) {
                        listener.onFileDone(i + 1, total, "闪卡: " + fcEntities.get(i).getFront(), "success", "");
                    }
                }
                result.setFlashcardCount(fc);
                result.getGeneratedModules().add("flashcard");
            }
            // 学习路径（需先有闪卡）
            if (doPath) {
                Long pathId = buildLearningPath(dto, categoryId, categoryName, dirDocs,
                        dirToCatId, catToDocIds, dirFlashcardIds, userId);
                result.setLearningPathId(pathId);
                result.setChapterCount(dirDocs.size());
                result.getGeneratedModules().add("path");
            }
            // 题库：线程池并行解析生成，主线程汇总批量持久化
            if (doQuiz) {
                List<QuizQuestion> qEntities = parallelGenerate(docs, doc -> generateQuizEntities(doc, doc.getCategoryId(), quizRule));
                saveBatch(quizQuestionMapper, qEntities);
                int q = qEntities.size();
                if (listener != null) {
                    int total = qEntities.size();
                    for (int i = 0; i < qEntities.size(); i++) {
                        listener.onFileDone(i + 1, total, "题库: " + qEntities.get(i).getTitle(), "success", "");
                    }
                }
                result.setQuizCount(q);
                result.getGeneratedModules().add("quiz");
            }
        }

        if (listener != null) {
            // 汇总结果由调用方（SSE 控制器）在 importAllWithProgress 返回后自行推送 complete 事件，
            // 此处仅触发 onComplete 占位，保证监听器生命周期完整。
            listener.onComplete(null);
        }
        result.setMessage("内容提炼采用规则模板（离线，不依赖 AI）。如需 AI 质量请配置 AI 服务后重试。");
        return result;
    }

    /**
     * 线程池并行执行文档级解析生成，聚合结果到主线程统一持久化。
     * <p>文本解析为 CPU 密集操作，并行可显著缩短大目录导入耗时。</p>
     */
    private <T> List<T> parallelGenerate(List<DocDocument> docs, java.util.function.Function<DocDocument, List<T>> fn) {
        if (docs.isEmpty()) return new ArrayList<>();
        int n = Math.min(docs.size(), Math.max(2, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<List<T>>> futures = new ArrayList<>();
            for (DocDocument doc : docs) {
                futures.add(pool.submit(() -> fn.apply(doc)));
            }
            List<T> result = new ArrayList<>();
            for (Future<List<T>> f : futures) {
                try {
                    result.addAll(f.get());
                } catch (Exception e) {
                    log.warn("并行生成单元异常：{}", e.getMessage());
                }
            }
            return result;
        } finally {
            pool.shutdown();
        }
    }

    /** 批量持久化（在事务内逐条 insert，保证实体 ID 回填）。 */
    private <E> void saveBatch(BaseMapper<E> mapper, List<E> entities) {
        for (E e : entities) {
            mapper.insert(e);
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ==================== 内部：知识库导入选项 ====================

    private com.knowflow.dto.KnowledgeImportOptionsDTO buildOptions(ObsidianImportDTO dto, Long categoryId) {
        com.knowflow.dto.KnowledgeImportOptionsDTO opt = new com.knowflow.dto.KnowledgeImportOptionsDTO();
        opt.setTargetCategoryId(categoryId);
        opt.setCreateSubCategories(dto.getCreateSubCategories() != null ? dto.getCreateSubCategories() : true);
        opt.setAutoTags(dto.getAutoTags() != null ? dto.getAutoTags() : true);
        opt.setAiTags(false);
        opt.setIncremental(dto.getIncremental() != null ? dto.getIncremental() : true);
        opt.setMaxContentChars(dto.getMaxContentChars() != null ? dto.getMaxContentChars() : 50000);
        return opt;
    }

    // ==================== 内部：图片兜底迁移 ====================

    private void collectImages(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isHidden()) continue;
            if (f.isDirectory()) collectImages(f, out);
            else if (IMG_EXT.contains(ext(f.getName()))) out.add(f);
        }
    }

    /**
     * 预处理：将 Obsidian 源目录复制为临时目录，统一图片引用为可导入格式。
     * <ul>
     *   <li>Markdown 中 {@code ![[任意路径/xxx.svg|说明]]} 重写为标准 {@code !(xxx.svg)}（取文件名）</li>
     *   <li>所有图片平铺复制到临时目录根，使 importDirectory 的 imageMap 能按文件名命中并迁移</li>
     * </ul>
     * 返回临时目录绝对路径。
     */
    private String prepareTempDir(String absPath, String batch) throws Exception {
        File root = new File(absPath);
        File temp = new File(System.getProperty("java.io.tmpdir"), "knowflow_obsidian_" + batch);
        deleteDir(temp);
        temp.mkdirs();
        // 复制并改写 md
        rewriteMdDir(root, root, temp);
        // 平铺复制图片到临时根
        List<File> imgs = new ArrayList<>();
        collectImages(root, imgs);
        for (File img : imgs) {
            Files.copy(img.toPath(), new File(temp, img.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return temp.getAbsolutePath();
    }

    private void rewriteMdDir(File root, File current, File tempTarget) throws Exception {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isHidden()) continue;
            if (f.isDirectory()) {
                File sub = new File(tempTarget, f.getName());
                sub.mkdirs();
                rewriteMdDir(root, f, sub);
            } else if ("md".equals(ext(f.getName()))) {
                String raw = new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                String out = OBS_IMG.matcher(raw).replaceAll(mr -> {
                    String name = new File(mr.group(1).trim()).getName();
                    String alt = mr.group(2) != null ? mr.group(2).trim() : "";
                    return "![](" + name + (alt.isEmpty() ? "" : " \"" + alt + "\"") + ")";
                });
                File target = new File(tempTarget, f.getName());
                Files.write(target.toPath(), out.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) deleteDir(c);
                else c.delete();
            }
        }
        dir.delete();
    }

    // ==================== 内部：学习路径 ====================

    private Long buildLearningPath(ObsidianImportDTO dto, Long categoryId, String categoryName,
                                    Map<String, List<String>> dirDocs,
                                    Map<String, Long> dirToCatId,
                                    Map<Long, List<Long>> catToDocIds,
                                    Map<String, List<Long>> dirFlashcardIds, Long userId) {
        LearningPath path = new LearningPath();
        path.setTitle(dto.getPathTitle() != null ? dto.getPathTitle() : categoryName);
        path.setDescription("由 Obsidian 目录「" + categoryName + "」自动生成的学习路径，涵盖导入文档与关联闪卡。");
        path.setLevel(dto.getLevel() != null ? dto.getLevel() : "INTERMEDIATE");
        path.setSortOrder(10);
        path.setOwnerUserId(userId);
        learningPathMapper.insert(path);
        Long pathId = path.getId();

        int order = 1;
        for (Map.Entry<String, List<String>> en : dirDocs.entrySet()) {
            String dir = en.getKey();
            Long subCat = dirToCatId.get(dir);
            List<Long> docIds = subCat != null ? catToDocIds.getOrDefault(subCat, new ArrayList<>()) : new ArrayList<>();
            List<Long> fcIds = dirFlashcardIds.getOrDefault(dir, new ArrayList<>());
            LearningChapter ch = new LearningChapter();
            ch.setPathId(pathId);
            ch.setTitle(chapterTitle(dir, categoryName));
            ch.setSortOrder(order++);
            ch.setDocIds(join(docIds));
            ch.setFlashcardIds(join(fcIds));
            learningChapterMapper.insert(ch);
        }
        return pathId;
    }

    private String chapterTitle(String dir, String root) {
        if (dir == null || dir.isEmpty()) return root + " · 根目录";
        return root + " · " + dir;
    }

    // ==================== 内部：闪卡规则模板生成 ====================

    /** 解析 Markdown 标题为问答闪卡实体（不直接落库，交由主线程批量持久化）。 */
    private List<LearningFlashcard> generateFlashcardEntities(DocDocument doc, Long userId, TemplateRule rule) {
        List<LearningFlashcard> out = new ArrayList<>();
        String content = doc.getContent();
        // 按模板指定的标题层级切分（rule.headingLevel：2→## / 3→### / 1→#）
        String[] parts = content.split("(?m)^#{1," + rule.headingLevel + "}\\s+");
        int made = 0;
        for (int i = 1; i < parts.length && made < rule.maxPerDoc; i++) {
            String block = parts[i];
            int nl = block.indexOf('\n');
            String heading = (nl >= 0 ? block.substring(0, nl) : block).trim();
            String body = (nl >= 0 ? block.substring(nl + 1) : "").trim();
            if (heading.isEmpty() || body.isEmpty()) continue;
            // 数据源绑定：关键词模式仅抽取含关键词的标题
            if (!rule.matchesBinding(heading, body)) continue;
            // 剥离 markdown 图片/链接噪音，保留纯文本答案
            String back = stripMd(body);
            if (back.length() < 10) continue;
            LearningFlashcard fc = new LearningFlashcard();
            fc.setUserId(userId);
            fc.setDocId(doc.getId());
            fc.setCategoryId(doc.getCategoryId());
            fc.setFront(stripMd(heading));
            fc.setBack(back.length() > 2000 ? back.substring(0, 2000) : back);
            fc.setDifficulty(2);
            fc.setTags("Obsidian导入");
            fc.setSourceType("IMPORT");
            out.add(fc);
            made++;
        }
        return out;
    }

    // ==================== 内部：题库规则模板生成 ====================

    /** 解析文档标题生成题库实体（不直接落库，交由主线程批量持久化）。 */
    private List<QuizQuestion> generateQuizEntities(DocDocument doc, Long categoryId, TemplateRule rule) {
        List<QuizQuestion> out = new ArrayList<>();
        String content = doc.getContent();
        String[] parts = content.split("(?m)^#{1," + rule.headingLevel + "}\\s+");
        boolean doShort = rule.questionTypes.isEmpty() || rule.questionTypes.contains("SHORT_ANSWER");
        boolean doJudge = rule.questionTypes.isEmpty() || rule.questionTypes.contains("JUDGE");
        int made = 0;
        for (int i = 1; i < parts.length && made < rule.maxPerDoc; i++) {
            String block = parts[i];
            int nl = block.indexOf('\n');
            String heading = (nl >= 0 ? block.substring(0, nl) : block).trim();
            String body = (nl >= 0 ? block.substring(nl + 1) : "").trim();
            if (heading.isEmpty() || body.isEmpty()) continue;
            // 数据源绑定：关键词模式仅抽取含关键词的标题
            if (!rule.matchesBinding(heading, body)) continue;
            String plainBody = stripMd(body);
            if (plainBody.length() < 10) continue;

            // 简答题：标题为问，正文为答
            if (doShort) {
                QuizQuestion saq = new QuizQuestion();
                saq.setCategoryId(categoryId);
                saq.setDocId(doc.getId());
                saq.setQuestionType("SHORT_ANSWER");
                saq.setTitle(stripMd(heading));
                saq.setContent(stripMd(heading));
                saq.setDifficulty(2);
                saq.setAnswer(plainBody.length() > 2000 ? plainBody.substring(0, 2000) : plainBody);
                saq.setExplanation("参考文档「" + doc.getTitle() + "」相关内容。");
                saq.setTags("Obsidian导入");
                saq.setSource(SRC_TAG);
                saq.setStatus(1);
                out.add(saq);
                made++;
            }

            // 判断题：用标题作为命题（带"是否/能不能"等问句则转为陈述）
            if (doJudge) {
                String prop = toProposition(stripMd(heading));
                if (prop != null) {
                    QuizQuestion tf = new QuizQuestion();
                    tf.setCategoryId(categoryId);
                    tf.setDocId(doc.getId());
                    tf.setQuestionType("TRUE_FALSE");
                    tf.setTitle("判断：" + prop);
                    tf.setContent("判断：" + prop);
                    tf.setDifficulty(2);
                    tf.setAnswer("A"); // 默认判定为正确（规则模板无法判定语义，统一标记"正确"供人工复核）
                    tf.setExplanation("参考文档「" + doc.getTitle() + "」。规则模板默认判定为正确，请人工复核。");
                    tf.setTags("Obsidian导入");
                    tf.setSource(SRC_TAG);
                    tf.setStatus(1);
                    out.add(tf);
                    made++;
                }
            }
        }
        return out;
    }

    private String toProposition(String heading) {
        // 已是问句（以？/? 结尾，或含"为什么/如何/什么/怎么"）不适合做判断题，返回 null
        if (heading.contains("？") || heading.contains("?")) return null;
        if (heading.matches(".*(为什么|如何|怎么|什么|怎样|是否|能否|区别|原理|流程|机制).*")) return null;
        // 去掉 Markdown 噪音后作为陈述命题
        return heading;
    }

    // ==================== 规则模板解析 ====================

    /**
     * 解析导入规则模板为生成参数。
     * <p>若 id 为空或解析失败，返回类型对应的内置默认规则（headingLevel=2、上限取常量、题型全开）。</p>
     */
    private TemplateRule resolveTemplate(Long id, String type) {
        if (id == null) {
            return type.equals("FLASHCARD")
                    ? new TemplateRule(2, MAX_FLASHCARD_PER_DOC, new HashSet<>(), "heading", "")
                    : new TemplateRule(2, MAX_QUIZ_PER_DOC, new HashSet<>(), "heading", "");
        }
        ImportTemplate t = importTemplateMapper.selectById(id);
        if (t == null || t.getDeleted() == 1 || !type.equals(t.getType()) || t.getEnabled() != 1) {
            return type.equals("FLASHCARD")
                    ? new TemplateRule(2, MAX_FLASHCARD_PER_DOC, new HashSet<>(), "heading", "")
                    : new TemplateRule(2, MAX_QUIZ_PER_DOC, new HashSet<>(), "heading", "");
        }
        int headingLevel = 2;
        int maxPerDoc = type.equals("FLASHCARD") ? MAX_FLASHCARD_PER_DOC : MAX_QUIZ_PER_DOC;
        Set<String> questionTypes = new HashSet<>();
        String bindMode = "heading";
        String bindPattern = "";
        try {
            JsonNode root = objectMapper.readTree(t.getContent());
            JsonNode rules = root.get("rules");
            if (rules != null) {
                JsonNode hl = rules.get("headingLevel");
                if (hl != null && hl.isInt()) headingLevel = hl.asInt();
                JsonNode mp = rules.get("maxPerDoc");
                if (mp != null && mp.isInt()) maxPerDoc = mp.asInt();
                JsonNode qt = rules.get("questionTypes");
                if (qt != null && qt.isArray()) {
                    for (JsonNode q : qt) questionTypes.add(q.asText());
                }
            }
            JsonNode binding = root.get("sourceBinding");
            if (binding != null) {
                JsonNode m = binding.get("mode");
                if (m != null) bindMode = m.asText();
                JsonNode p = binding.get("pattern");
                if (p != null) bindPattern = p.asText();
            }
        } catch (Exception e) {
            log.warn("解析导入规则模板({})失败，使用默认规则：{}", id, e.getMessage());
        }
        return new TemplateRule(headingLevel, maxPerDoc, questionTypes, bindMode, bindPattern);
    }

    /** 规则模板解析结果，驱动闪卡/题库抽取。 */
    private static class TemplateRule {
        final int headingLevel;
        final int maxPerDoc;
        final Set<String> questionTypes;
        final String bindMode;
        final String bindPattern;

        TemplateRule(int headingLevel, int maxPerDoc, Set<String> questionTypes, String bindMode, String bindPattern) {
            this.headingLevel = headingLevel;
            this.maxPerDoc = maxPerDoc;
            this.questionTypes = questionTypes;
            this.bindMode = bindMode;
            this.bindPattern = bindPattern;
        }

        /** 数据源绑定匹配：heading 模式全量抽取；keyword 模式仅抽取含关键词的标题/正文。 */
        boolean matchesBinding(String heading, String body) {
            if (!"keyword".equals(bindMode) || bindPattern == null || bindPattern.isEmpty()) {
                return true;
            }
            return heading.contains(bindPattern) || body.contains(bindPattern);
        }
    }

    // ==================== 工具 ====================

    private String dirOf(String relPath) {
        int idx = relPath.lastIndexOf('/');
        return idx < 0 ? "" : relPath.substring(0, idx);
    }

    /** 由文档所属分类反查其目录名（用于把闪卡归入对应章节）。 */
    private String dirOfByCat(Long catId, Map<String, Long> dirToCatId, Long rootCatId) {
        if (catId == null || catId.equals(rootCatId)) return "";
        for (Map.Entry<String, Long> en : dirToCatId.entrySet()) {
            if (catId.equals(en.getValue())) return en.getKey();
        }
        return "";
    }

    private String join(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private String stripMd(String md) {
        if (md == null) return "";
        return md.replaceAll("<img[^>]*>", "[图片]")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")
                .replaceAll("!\\[\\[[^\\]]*\\]\\]", "")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("\\*\\*([^*]*)\\*\\*", "$1")
                .replaceAll("#+\\s*", "")
                .replaceAll("\\n{2,}", "\n").trim();
    }

    private String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private List<Long> descendantCategoryIds(Long rootId) {
        List<Long> result = new ArrayList<>();
        result.add(rootId);
        List<DocCategory> children = docCategoryMapper.selectList(
                new LambdaQueryWrapper<DocCategory>().eq(DocCategory::getParentId, rootId));
        for (DocCategory c : children) {
            result.addAll(descendantCategoryIds(c.getId()));
        }
        return result;
    }

    private String UUID() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
