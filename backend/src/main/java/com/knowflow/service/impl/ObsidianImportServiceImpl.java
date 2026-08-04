package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.ObsidianImportDTO;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.service.KnowledgeImportService;
import com.knowflow.service.ObsidianImportService;
import com.knowflow.service.PathImportService;
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
        ObsidianImportResultVO result = new ObsidianImportResultVO();
        result.setGeneratedModules(new ArrayList<>());

        // 1. 解析并扫描路径
        String absPath = pathImportService.resolvePath(dto.getPath(), null);
        result.setAbsolutePath(absPath);
        PathImportScanVO scan = pathImportService.scanForImport(absPath);
        // 收集所有 md 相对路径（用于导入后反查 docId）与子目录结构
        List<String> mdPaths = new ArrayList<>();
        Map<String, List<String>> dirDocs = new LinkedHashMap<>();
        for (PathImportScanVO.FileEntry e : scan.getFiles()) {
            if ("doc".equals(e.getType())) {
                mdPaths.add(e.getPath());
                String dir = dirOf(e.getPath());
                dirDocs.computeIfAbsent(dir, k -> new ArrayList<>()).add(e.getPath());
            }
        }

        // 2. 确定目标知识库（复用以存在或新建）
        Long categoryId = dto.getTargetCategoryId();
        String categoryName;
        if (categoryId == null) {
            DocCategory cat = new DocCategory();
            cat.setName(scan.getRootName());
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

        // 3. 导入知识库（同步，复用既有流程）
        //    由于既有 importDirectory 对 Obsidian ![[abs/xxx.svg|alt]] 图片语法处理会清空正文，
        //    这里先预处理：把源目录复制为临时目录，将 ![[...]] 重写为标准 Markdown !(文件名)，
        //    并把图片平铺到临时目录，使 importDirectory 能正常迁移图片、保留正文。
        if (dto.getModules() == null || dto.getModules().contains("knowledge")) {
            try {
                String batch = UUID().substring(0, 12);
                String tempDir = prepareTempDir(absPath, batch);
                MultipartFile[] files = pathImportService.collectFiles(tempDir);
                knowledgeImportService.importDirectory(files, buildOptions(dto, categoryId), userId);
                result.getGeneratedModules().add("knowledge");
            } catch (Exception e) {
                log.error("知识库导入异常: {}", e.getMessage(), e);
                throw new RuntimeException("知识库导入失败：" + e.getMessage());
            }
        }

        // 4. 收集导入的文档（按子分类分组）。
        //    注：路径导入模式未必写入 sourcePath，故直接按分类树查询全部文档建立关联，
        //    避免依赖 sourcePath 精确匹配。mdPaths 仅用于图片兜底读取源文件。
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
        result.setDocCount(allDocIds.size());

        // 5. 图片已在第 3 步由 importDirectory 统一迁移（临时目录已平铺图片并重写为标准语法）。

        // 6. 按需生成其余模块
        boolean doPath = dto.getModules() != null && dto.getModules().contains("path");
        boolean doFlash = dto.getModules() != null && dto.getModules().contains("flashcard");
        boolean doQuiz = dto.getModules() != null && dto.getModules().contains("quiz");

        // 加载规则模板（驱动闪卡/题库的抽取层级、数量上限、题型与数据源绑定）
        TemplateRule flashRule = resolveTemplate(dto.getFlashcardTemplateId(), "FLASHCARD");
        TemplateRule quizRule = resolveTemplate(dto.getQuizTemplateId(), "QUIZ");

        // 学习路径必须在闪卡之前，章节需要挂载闪卡
        Map<String, List<Long>> dirFlashcardIds = new HashMap<>();
        if (doPath || doFlash || doQuiz) {
            // 先生成闪卡（逐文档）
            if (doFlash) {
                int fc = 0;
                for (Long docId : allDocIds) {
                    DocDocument doc = docDocumentMapper.selectById(docId);
                    if (doc == null) continue;
                    List<Long> ids = generateFlashcards(doc, userId, flashRule);
                    fc += ids.size();
                    String dir = dirOfByCat(doc.getCategoryId(), dirToCatId, categoryId);
                    dirFlashcardIds.computeIfAbsent(dir, k -> new ArrayList<>()).addAll(ids);
                }
                result.setFlashcardCount(fc);
                result.getGeneratedModules().add("flashcard");
            }
            // 生成学习路径 + 章节
            if (doPath) {
                Long pathId = buildLearningPath(dto, categoryId, categoryName, dirDocs,
                        dirToCatId, catToDocIds, dirFlashcardIds, userId);
                result.setLearningPathId(pathId);
                result.setChapterCount(dirDocs.size());
                result.getGeneratedModules().add("path");
            }
            // 生成题库
            if (doQuiz) {
                int q = 0;
                for (Long docId : allDocIds) {
                    DocDocument doc = docDocumentMapper.selectById(docId);
                    if (doc == null) continue;
                    q += generateQuiz(doc, doc.getCategoryId(), quizRule);
                }
                result.setQuizCount(q);
                result.getGeneratedModules().add("quiz");
            }
        }

        result.setMessage("内容提炼采用规则模板（离线，不依赖 AI）。如需 AI 质量请配置 AI 服务后重试。");
        return result;
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

    /** 解析 Markdown 标题为问答闪卡，抽取层级/数量上限/数据源绑定由模板规则驱动。 */
    private List<Long> generateFlashcards(DocDocument doc, Long userId, TemplateRule rule) {
        List<Long> ids = new ArrayList<>();
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
            learningFlashcardMapper.insert(fc);
            ids.add(fc.getId());
            made++;
        }
        return ids;
    }

    // ==================== 内部：题库规则模板生成 ====================

    /** 解析文档标题生成题库，题型组合/抽取层级/数量上限/数据源绑定由模板规则驱动。 */
    private int generateQuiz(DocDocument doc, Long categoryId, TemplateRule rule) {
        int n = 0;
        String content = doc.getContent();
        String[] parts = content.split("(?m)^#{1," + rule.headingLevel + "}\\s+");
        boolean doShort = rule.questionTypes.isEmpty() || rule.questionTypes.contains("SHORT_ANSWER");
        boolean doJudge = rule.questionTypes.isEmpty() || rule.questionTypes.contains("JUDGE");
        for (int i = 1; i < parts.length && n < rule.maxPerDoc; i++) {
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
                quizQuestionMapper.insert(saq);
                n++;
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
                    quizQuestionMapper.insert(tf);
                    n++;
                }
            }
        }
        return n;
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
