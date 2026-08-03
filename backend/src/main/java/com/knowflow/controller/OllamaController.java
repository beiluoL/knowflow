package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.OllamaConfig;
import com.knowflow.service.OllamaService;
import com.knowflow.vo.OllamaModelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Ollama 本地模型管理接口。
 * <p>
 * 提供连接测试、模型列表获取、模型加载/卸载/删除、配置持久化与导入导出。
 */
@Slf4j
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
@Tag(name = "Ollama 管理", description = "本地 Ollama 模型连接、列表、加载卸载与配置")
public class OllamaController {

    private final OllamaService ollamaService;

    // ==================== 配置管理 ====================

    @Operation(summary = "获取当前用户的 Ollama 配置")
    @GetMapping("/config")
    public Result<OllamaConfig> getConfig() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ollamaService.getOrCreateConfig(userId));
    }

    @Operation(summary = "更新 Ollama 配置（服务地址、默认模型、参数预设）")
    @PutMapping("/config")
    public Result<OllamaConfig> updateConfig(@RequestBody OllamaConfig dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ollamaService.updateConfig(userId, dto));
    }

    @Operation(summary = "导出 Ollama 配置（用于备份）")
    @GetMapping("/config/export")
    public Result<OllamaConfig> exportConfig() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ollamaService.getOrCreateConfig(userId));
    }

    @Operation(summary = "导入 Ollama 配置（从备份恢复）")
    @PostMapping("/config/import")
    public Result<OllamaConfig> importConfig(@RequestBody OllamaConfig dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(ollamaService.updateConfig(userId, dto));
    }

    // ==================== 连接测试 ====================

    @Operation(summary = "测试 Ollama 服务连接（传入 baseUrl 或使用已保存配置）")
    @PostMapping("/test")
    public Result<Map<String, Object>> testConnection(@RequestBody(required = false) Map<String, String> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        String baseUrl = body != null ? body.get("baseUrl") : null;
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ollamaService.getOrCreateConfig(userId).getBaseUrl();
        }
        return Result.success(ollamaService.testConnection(baseUrl));
    }

    // ==================== 模型列表 ====================

    @Operation(summary = "获取 Ollama 已安装的模型列表")
    @GetMapping("/models")
    public Result<List<OllamaModelVO>> listModels(
            @RequestParam(required = false) String baseUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ollamaService.getOrCreateConfig(userId).getBaseUrl();
        }
        return Result.success(ollamaService.listModels(baseUrl));
    }

    // ==================== 模型加载/卸载 ====================

    @Operation(summary = "加载模型到内存")
    @PostMapping("/models/{modelName}/load")
    public Result<Map<String, Object>> loadModel(
            @PathVariable String modelName,
            @RequestParam(required = false) String baseUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ollamaService.getOrCreateConfig(userId).getBaseUrl();
        }
        return Result.success(ollamaService.loadModel(baseUrl, modelName));
    }

    @Operation(summary = "从内存卸载模型")
    @PostMapping("/models/{modelName}/unload")
    public Result<Map<String, Object>> unloadModel(
            @PathVariable String modelName,
            @RequestParam(required = false) String baseUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ollamaService.getOrCreateConfig(userId).getBaseUrl();
        }
        return Result.success(ollamaService.unloadModel(baseUrl, modelName));
    }

    @Operation(summary = "删除已安装的模型")
    @DeleteMapping("/models/{modelName}")
    public Result<Map<String, Object>> deleteModel(
            @PathVariable String modelName,
            @RequestParam(required = false) String baseUrl) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ollamaService.getOrCreateConfig(userId).getBaseUrl();
        }
        return Result.success(ollamaService.deleteModel(baseUrl, modelName));
    }

    // ==================== 快捷：将 Ollama 模型添加为 Agent 配置 ====================

    @Operation(summary = "将指定 Ollama 模型添加为编程 Agent 的模型配置（自动创建 UserAiConfig）")
    @PostMapping("/models/{modelName}/add-to-agent")
    public Result<Map<String, Object>> addToAgent(@PathVariable String modelName) {
        Long userId = SecurityUtils.getCurrentUserId();
        OllamaConfig config = ollamaService.getOrCreateConfig(userId);
        // 这里只返回需要的信息，实际创建由前端调用 ai-config 接口完成
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("provider", "ollama");
        result.put("providerType", "LOCAL");
        result.put("baseUrl", config.getBaseUrl() + "/v1"); // OpenAI 兼容端点
        result.put("model", modelName);
        result.put("apiKey", "local");
        result.put("capability", "STANDARD");
        result.put("displayName", "Ollama · " + modelName);
        return Result.success(result);
    }
}
