package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.CodeWorkspaceFileVO;
import com.knowflow.service.CodeWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 代码工作区接口（SC1-IDE-02 可重置实验沙箱）。
 * <p>需要登录（JWT）。按当前用户隔离维护持久化文件目录，支持列表 / 新建覆盖 / 删除 / 重置。
 */
@Slf4j
@Tag(name = "代码工作区")
@RestController
@RequestMapping("/api/code/workspace")
@RequiredArgsConstructor
public class CodeWorkspaceController {

    private final CodeWorkspaceService workspaceService;

    @Operation(summary = "列出我的工作区文件")
    @GetMapping("/files")
    public Result<List<CodeWorkspaceFileVO>> listFiles() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(workspaceService.listFiles(userId));
    }

    @Operation(summary = "新建或覆盖写入工作区文件")
    @PostMapping("/files")
    public Result<CodeWorkspaceFileVO> saveFile(@Valid @RequestBody SaveFileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CodeWorkspaceFileVO vo = workspaceService.saveFile(userId, request.getPath(), request.getContent());
        return Result.success(vo);
    }

    @Operation(summary = "删除工作区文件")
    @DeleteMapping("/files")
    public Result<Void> deleteFile(@RequestParam @NotBlank String path) {
        Long userId = SecurityUtils.getCurrentUserId();
        workspaceService.deleteFile(userId, path);
        return Result.success();
    }

    @Operation(summary = "重置工作区（清空全部文件）")
    @PostMapping("/reset")
    public Result<Void> reset() {
        Long userId = SecurityUtils.getCurrentUserId();
        workspaceService.reset(userId);
        return Result.success();
    }

    @Data
    public static class SaveFileRequest {
        @NotBlank(message = "文件路径不能为空")
        private String path;
        private String content;
    }
}
