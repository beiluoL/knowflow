package com.knowflow.controller.admin;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.KbMember;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.CategoryService;
import com.knowflow.service.DocService;
import com.knowflow.service.KbMemberService;
import com.knowflow.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 管理员分类（知识库）管理 REST 接口。
 * 扩展：创建时绑定 Owner；批量导入文档；导出知识库 ZIP。
 */
@Tag(name = "管理员分类管理")
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@Slf4j
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final KbMemberService kbMemberService;
    private final DocService docService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "分类列表（顶级知识库，系统 ADMIN 看全部；其他用户看自己有成员权限的）")
    @GetMapping
    public Result<List<DocCategory>> list() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<DocCategory> all = categoryService.list();
        if (SecurityUtils.isAdmin()) {
            return Result.success(all);
        }
        // 非 ADMIN：仅返回当前用户作为成员（含 Owner/Editor/Reader）的知识库
        Set<Long> categoryIds = new HashSet<>();
        for (DocCategory c : all) {
            if (kbMemberService.canView(c.getId(), currentUserId)) {
                categoryIds.add(c.getId());
            }
        }
        return Result.success(all.stream().filter(c -> categoryIds.contains(c.getId())).toList());
    }

    @Operation(summary = "分类树（管理端，含已禁用）")
    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.getCategoryTreeForAdmin());
    }

    @Operation(summary = "新增分类（知识库）：自动绑定创建者为 Owner")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<DocCategory> add(@RequestBody DocCategory category) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 校验 parentId 合法性（父分类存在、非自环、不形成环、深度 ≤ 3）
        categoryService.validateParentId(null, category.getParentId());
        // 默认 status=1（启用）
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        category.setOwnerId(currentUserId);
        categoryService.save(category);
        // 同时创建 OWNER 成员记录
        kbMemberService.addMember(category.getId(), currentUserId, KbMember.ROLE_OWNER, null);
        return Result.success(category);
    }

    @Operation(summary = "更新分类")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DocCategory category) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        DocCategory existed = categoryService.getById(id);
        if (existed == null) {
            throw new BusinessException("知识库不存在");
        }
        if (!kbMemberService.isOwner(id, currentUserId)) {
            throw new BusinessException("无权修改该知识库");
        }
        // 校验 parentId 合法性（防止把分类移到自己/子孙节点下形成环）
        categoryService.validateParentId(id, category.getParentId());
        category.setId(id);
        // ownerId 不可随意修改，保留原值
        category.setOwnerId(existed.getOwnerId());
        categoryService.updateById(category);
        return Result.success();
    }

    @Operation(summary = "删除分类（仅 Owner / 系统 ADMIN）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!kbMemberService.isOwner(id, currentUserId)) {
            throw new BusinessException("无权删除该知识库");
        }
        categoryService.removeCategory(id);
        return Result.success();
    }

    // ======================== 批量导入文档 ========================

    @Data
    public static class ImportResultVO {
        private int total;
        private int success;
        private int failed;
        private List<String> failedNames;
        private List<String> successNames;
    }

    @Operation(summary = "批量导入文档（支持 .zip / .md / .txt / .json）")
    @PostMapping(value = "/{categoryId}/import-docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public Result<ImportResultVO> importDocs(
            @PathVariable Long categoryId,
            @RequestPart("file") MultipartFile file) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!kbMemberService.canEditDocs(categoryId, currentUserId)) {
            throw new BusinessException("无权向该知识库导入文档");
        }
        DocCategory cat = categoryService.getById(categoryId);
        if (cat == null) {
            throw new BusinessException("知识库不存在");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        List<ParsedDoc> parsed = new ArrayList<>();
        try (InputStream is = file.getInputStream()) {
            if (original.endsWith(".zip")) {
                parsed.addAll(parseZip(is));
            } else if (original.endsWith(".md") || original.endsWith(".txt")) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String name = file.getOriginalFilename() == null
                        ? "未命名"
                        : file.getOriginalFilename().replaceFirst("\\.(md|txt)$", "");
                parsed.add(ParsedDoc.of(name, content, parseTagsFromFrontMatter(content)));
            } else if (original.endsWith(".json")) {
                parsed.addAll(parseJson(new String(is.readAllBytes(), StandardCharsets.UTF_8)));
            } else {
                throw new BusinessException("不支持的文件类型，支持 .zip / .md / .txt / .json");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("导入文件解析失败: {}", e.getMessage(), e);
            throw new BusinessException("文件解析失败：" + e.getMessage());
        }
        if (parsed.isEmpty()) {
            throw new BusinessException("未找到可导入的文档内容");
        }
        ImportResultVO result = new ImportResultVO();
        result.setTotal(parsed.size());
        result.setSuccessNames(new ArrayList<>());
        result.setFailedNames(new ArrayList<>());
        for (ParsedDoc d : parsed) {
            if (StrUtil.isBlank(d.getTitle())) {
                d.setTitle("未命名-" + UUID.randomUUID().toString().substring(0, 6));
            }
            try {
                DocDocument doc = new DocDocument();
                doc.setTitle(d.getTitle());
                doc.setContent(cleanFrontMatter(d.getContent()));
                doc.setCategoryId(categoryId);
                doc.setIcon(null);
                doc.setTags(d.getTags() == null ? null : String.join(",", d.getTags()));
                doc.setSummary(buildSummary(doc.getContent()));
                doc.setViewCount(0);
                doc.setReadCount(0);
                doc.setFavoriteCount(0);
                doc.setWordCount(StrUtil.length(doc.getContent()));
                doc.setDifficulty(1);
                doc.setSortOrder(0);
                doc.setStatus(1);
                docService.save(doc);
                categoryService.incrementDocCount(categoryId);
                result.getSuccessNames().add(d.getTitle());
            } catch (Exception e) {
                log.warn("单篇文档导入失败: {}", d.getTitle(), e);
                result.getFailedNames().add(d.getTitle() + "（" + e.getMessage() + "）");
            }
        }
        result.setSuccess(result.getSuccessNames().size());
        result.setFailed(result.getFailedNames().size());
        return Result.success(result);
    }

    // ======================== 导出知识库 ========================

    @Operation(summary = "导出知识库为 ZIP（docs/*.md + meta.json）")
    @GetMapping("/{categoryId}/export")
    public ResponseEntity<ByteArrayResource> export(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "zip") String format) throws Exception {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!kbMemberService.canView(categoryId, currentUserId)) {
            throw new BusinessException("无权导出该知识库");
        }
        DocCategory cat = categoryService.getById(categoryId);
        if (cat == null) {
            throw new BusinessException("知识库不存在");
        }
        List<DocDocument> docs = docService.listByCategory(categoryId);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            // ===== meta.json =====
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            Map<String, Object> kb = new LinkedHashMap<>();
            kb.put("id", cat.getId());
            kb.put("name", cat.getName());
            kb.put("code", cat.getCode());
            kb.put("description", cat.getDescription());
            kb.put("icon", cat.getIcon());
            kb.put("docCount", docs.size());
            kb.put("format", format);
            meta.put("knowledgeBase", kb);

            List<Map<String, Object>> docMetaList = new ArrayList<>();
            for (DocDocument doc : docs) {
                String safeName = sanitizeFileName(doc.getTitle()) + "_" + doc.getId() + ".md";
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("id", doc.getId());
                dm.put("title", doc.getTitle());
                dm.put("tags", doc.getTags() == null ? List.of() : Arrays.asList(doc.getTags().split(",")));
                dm.put("difficulty", doc.getDifficulty());
                dm.put("wordCount", doc.getWordCount());
                dm.put("file", "docs/" + safeName);
                dm.put("createTime", doc.getCreateTime() == null ? null : doc.getCreateTime().toString());
                dm.put("updateTime", doc.getUpdateTime() == null ? null : doc.getUpdateTime().toString());
                docMetaList.add(dm);
            }
            meta.put("docs", docMetaList);
            zos.putNextEntry(new ZipEntry("meta.json"));
            zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
            zos.closeEntry();

            // ===== docs/*.md =====
            int idx = 0;
            for (DocDocument doc : docs) {
                String safeName = sanitizeFileName(doc.getTitle()) + "_" + doc.getId() + ".md";
                zos.putNextEntry(new ZipEntry("docs/" + safeName));
                String front = buildFrontMatter(doc);
                byte[] body = (front + (doc.getContent() == null ? "" : doc.getContent()))
                        .getBytes(StandardCharsets.UTF_8);
                zos.write(body);
                zos.closeEntry();
                idx++;
            }
            zos.finish();
        }
        byte[] bytes = bos.toByteArray();
        String filename = URLEncoder.encode(sanitizeFileName(cat.getName()) + ".zip", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    // ======================== 解析/导出辅助方法 ========================

    /** 解析 ZIP：遍历 entry，遇到 .md/.txt 以文件名作为标题创建文档；忽略目录与非文档文件。 */
    private List<ParsedDoc> parseZip(InputStream is) throws Exception {
        List<ParsedDoc> result = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase();
                boolean isMd = name.endsWith(".md");
                boolean isTxt = name.endsWith(".txt");
                boolean isJson = name.endsWith(".json");
                if (!isMd && !isTxt && !isJson) {
                    continue;
                }
                String raw = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                // 去除 entry 前导路径，只取文件名（不含后缀）
                String baseName = entry.getName();
                int slash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
                if (slash >= 0) baseName = baseName.substring(slash + 1);
                if (isMd) baseName = baseName.replaceFirst("\\.md$", "");
                else if (isTxt) baseName = baseName.replaceFirst("\\.txt$", "");
                else {
                    // json 单独作为批处理
                    result.addAll(parseJson(raw));
                    continue;
                }
                ParsedDoc d = ParsedDoc.of(baseName, raw, parseTagsFromFrontMatter(raw));
                result.add(d);
            }
        }
        return result;
    }

    /**
     * 解析批量 JSON：
     * 支持数组：[{title, content, tags?, summary?, difficulty?}, ...]
     */
    private List<ParsedDoc> parseJson(String raw) throws Exception {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        Object node = objectMapper.readValue(raw, Object.class);
        List<ParsedDoc> result = new ArrayList<>();
        if (node instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> map) {
                    ParsedDoc d = new ParsedDoc();
                    d.setTitle(Objects.toString(map.get("title"), null));
                    d.setContent(Objects.toString(map.get("content"), ""));
                    Object tags = map.get("tags");
                    if (tags instanceof List<?> tl) {
                        d.setTags(tl.stream().map(Object::toString).toList());
                    }
                    result.add(d);
                }
            }
        }
        return result;
    }

    /** 简易 Front Matter 解析：提取 tags: [a,b] */
    private List<String> parseTagsFromFrontMatter(String content) {
        if (StrUtil.isBlank(content)) return null;
        try {
            if (!content.startsWith("---")) return null;
            int end = content.indexOf("---", 3);
            if (end < 0) return null;
            String fm = content.substring(3, end);
            for (String line : fm.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.toLowerCase().startsWith("tags:")) {
                    String rest = trimmed.substring(5).trim();
                    if (rest.startsWith("[") && rest.endsWith("]")) {
                        String inner = rest.substring(1, rest.length() - 1);
                        return Arrays.stream(inner.split(","))
                                .map(String::trim).filter(StrUtil::isNotBlank).toList();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String cleanFrontMatter(String content) {
        if (StrUtil.isBlank(content)) return content;
        if (!content.startsWith("---")) return content;
        int end = content.indexOf("---", 3);
        if (end < 0) return content;
        String rest = content.substring(end + 3);
        if (rest.startsWith("\r\n")) return rest.substring(2);
        if (rest.startsWith("\n")) return rest.substring(1);
        return rest;
    }

    private String buildSummary(String content) {
        if (StrUtil.isBlank(content)) return "";
        String plain = content.replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("#+\\s", "")
                .replaceAll("[#*_>\\-`]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return StrUtil.maxLength(plain, 200);
    }

    private String buildFrontMatter(DocDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: \"").append(escapeYaml(doc.getTitle())).append("\"\n");
        if (doc.getId() != null) sb.append("id: ").append(doc.getId()).append("\n");
        if (doc.getDifficulty() != null) sb.append("difficulty: ").append(doc.getDifficulty()).append("\n");
        if (StrUtil.isNotBlank(doc.getTags())) {
            sb.append("tags: [").append(doc.getTags()).append("]\n");
        }
        if (StrUtil.isNotBlank(doc.getSummary())) {
            sb.append("summary: \"").append(escapeYaml(doc.getSummary())).append("\"\n");
        }
        sb.append("---\n\n");
        return sb.toString();
    }

    private String escapeYaml(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sanitizeFileName(String s) {
        if (StrUtil.isBlank(s)) return "未命名";
        return s.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").replaceAll("\\s+", "_");
    }

    @Data
    public static class ParsedDoc {
        private String title;
        private String content;
        private List<String> tags;

        public static ParsedDoc of(String title, String content, List<String> tags) {
            ParsedDoc d = new ParsedDoc();
            d.setTitle(title);
            d.setContent(content);
            d.setTags(tags);
            return d;
        }
    }
}
