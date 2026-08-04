package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.ObsidianImportDTO;
import com.knowflow.service.KbMemberService;
import com.knowflow.service.ObsidianImportService;
import com.knowflow.service.PathImportService;
import com.knowflow.vo.ObsidianImportResultVO;
import com.knowflow.vo.PathImportScanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Obsidian 目录一键导入接口：扫描本地目录 → 自动生成四模块（知识库/学习路径/闪卡/题库）。
 * <p>支持前端选择目录、输入绝对/相对路径；支持模块单选/多选导入。
 */
@Slf4j
@Tag(name = "Obsidian 目录一键导入")
@RestController
@RequestMapping("/api/obsidian/import")
@RequiredArgsConstructor
public class ObsidianImportController {

    private final ObsidianImportService obsidianImportService;
    private final PathImportService pathImportService;
    private final KbMemberService kbMemberService;

    @Operation(summary = "扫描本地路径，返回待导入的 Markdown 文件结构（预览）")
    @GetMapping("/scan")
    public Result<PathImportScanVO> scan(
            @RequestParam("path") String path,
            @RequestParam(value = "relativeTo", required = false) String relativeTo) {
        String abs = pathImportService.resolvePath(path, relativeTo);
        PathImportScanVO vo = pathImportService.scanForImport(abs);
        return Result.success(vo);
    }

    @Operation(summary = "一键导入并生成所选模块（知识库/学习路径/闪卡/题库）")
    @PostMapping("/generate")
    public Result<ObsidianImportResultVO> generate(@RequestBody ObsidianImportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (dto.getTargetCategoryId() != null
                && !kbMemberService.canEditDocs(dto.getTargetCategoryId(), userId)) {
            return Result.error("无权向该知识库导入文档（需 Owner 或 Editor 权限）");
        }
        if (dto.getModules() == null || dto.getModules().isEmpty()) {
            dto.setModules(java.util.List.of("knowledge", "path", "flashcard", "quiz"));
        }
        ObsidianImportResultVO result = obsidianImportService.importAll(dto, userId);
        return Result.success("导入完成", result);
    }
}
