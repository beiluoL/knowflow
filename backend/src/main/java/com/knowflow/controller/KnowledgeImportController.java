package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.KnowledgeImportOptionsDTO;
import com.knowflow.service.KnowledgeImportService;
import com.knowflow.vo.KnowledgeImportResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库目录批量导入接口。
 * <p>
 * 支持 Obsidian 仓库 / 本地目录批量导入：
 * <ul>
 *     <li>前端通过 {@code <input webkitdirectory>} 选择目录，上传所有文件</li>
 *     <li>每个文件的 originalFilename 为相对路径（如 Notes/AI/ML.md）</li>
 *     <li>后端自动解析目录层级→分类树、迁移图片、重写链接、生成标签、增量去重</li>
 * </ul>
 */
@Slf4j
@Tag(name = "知识库导入接口")
@RestController
@RequestMapping("/api/knowledge/import")
@RequiredArgsConstructor
public class KnowledgeImportController {

    private final KnowledgeImportService knowledgeImportService;

    /**
     * 批量导入目录文件到知识库。
     * <p>
     * 请求体为 multipart/form-data：
     * <ul>
     *     <li>files: 文件数组（含相对路径作为 originalFilename）</li>
     *     <li>targetCategoryId: 目标知识库 ID（必填）</li>
     *     <li>createSubCategories: 是否按目录创建子分类（默认 true）</li>
     *     <li>autoTags: 是否自动生成标签（默认 true）</li>
     *     <li>aiTags: 是否启用 AI 智能打标（默认 false）</li>
     *     <li>incremental: 是否启用增量去重（默认 true）</li>
     *     <li>maxContentChars: 单篇正文上限（默认 50000）</li>
     * </ul>
     */
    @Operation(summary = "批量导入目录到知识库")
    @PostMapping
    public Result<KnowledgeImportResultVO> importDirectory(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("targetCategoryId") Long targetCategoryId,
            @RequestParam(value = "createSubCategories", defaultValue = "true") Boolean createSubCategories,
            @RequestParam(value = "autoTags", defaultValue = "true") Boolean autoTags,
            @RequestParam(value = "aiTags", defaultValue = "false") Boolean aiTags,
            @RequestParam(value = "incremental", defaultValue = "true") Boolean incremental,
            @RequestParam(value = "maxContentChars", defaultValue = "50000") Integer maxContentChars) {

        Long userId = SecurityUtils.getCurrentUserId();

        KnowledgeImportOptionsDTO options = new KnowledgeImportOptionsDTO();
        options.setTargetCategoryId(targetCategoryId);
        options.setCreateSubCategories(createSubCategories);
        options.setAutoTags(autoTags);
        options.setAiTags(aiTags);
        options.setIncremental(incremental);
        options.setMaxContentChars(maxContentChars);

        log.info("知识库目录导入: userId={}, targetCategoryId={}, fileCount={}",
                userId, targetCategoryId, files != null ? files.length : 0);

        KnowledgeImportResultVO result = knowledgeImportService.importDirectory(files, options, userId);
        return Result.success("导入完成", result);
    }
}
