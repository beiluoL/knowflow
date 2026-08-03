package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.CodeGenDTO;
import com.knowflow.service.CodeGenService;
import com.knowflow.service.OllamaService;
import com.knowflow.vo.CodeGenResultVO;
import com.knowflow.vo.OllamaModelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地代码生成接口（Ollama + deepseek-coder）。
 * <p>
 * 面向「一句话生成可落盘代码」场景：前端提交自然语言指令，后端返回结构化的文件列表，
 * 由前端通过浏览器 File System Access API 让用户选择目录并写入磁盘。
 * <p>
 * 之所以由前端落盘而不是后端写文件：后端进程运行在服务器侧，无权访问用户本机目录；
 * 浏览器目录选择器能在用户显式授权下安全写入本地磁盘，这也符合「用户指定目录」的需求。
 */
@Slf4j
@RestController
@RequestMapping("/api/code-gen")
@RequiredArgsConstructor
@Tag(name = "本地代码生成", description = "基于本地 Ollama deepseek-coder 的自然语言生成代码")
public class CodeGenController {

    private final CodeGenService codeGenService;
    private final OllamaService ollamaService;

    @Operation(summary = "根据自然语言指令生成代码文件")
    @PostMapping("/generate")
    public Result<CodeGenResultVO> generate(@Valid @RequestBody CodeGenDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("代码生成请求: userId={}, promptLength={}", userId, dto.getPrompt().length());
        return Result.success(codeGenService.generate(userId, dto));
    }

    /**
     * 生成前的环境自检：确认 Ollama 已启动且目标模型已安装。
     * <p>
     * 前端在用户发起生成前先调此接口，可以把「服务没开 / 模型没装」这类问题
     * 提前暴露成可操作的提示，而不是让用户等待几分钟后拿到一个超时错误。
     */
    @Operation(summary = "检查 Ollama 服务与代码模型可用性")
    @GetMapping("/health")
    public Result<Map<String, Object>> health(@RequestParam(required = false) String model) {
        Long userId = SecurityUtils.getCurrentUserId();
        String baseUrl = ollamaService.getOrCreateConfig(userId).getBaseUrl();
        String targetModel = (model == null || model.isBlank()) ? CodeGenService.DEFAULT_MODEL : model.trim();

        Map<String, Object> conn = ollamaService.testConnection(baseUrl);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", baseUrl);
        result.put("targetModel", targetModel);
        result.put("serviceOk", conn.get("ok"));

        if (!Boolean.TRUE.equals(conn.get("ok"))) {
            result.put("modelInstalled", false);
            result.put("error", conn.get("error"));
            result.put("hint", "请先在终端执行 ollama serve 启动本地服务");
            return Result.success(result);
        }

        List<OllamaModelVO> models = ollamaService.listModels(baseUrl);
        // Ollama 模型名可能带 :latest 后缀，做前缀匹配避免误判为未安装
        boolean installed = models.stream().anyMatch(m -> m.getName() != null
                && (m.getName().equals(targetModel) || m.getName().startsWith(targetModel + ":")
                || targetModel.equals(stripTag(m.getName()))));
        result.put("modelInstalled", installed);
        result.put("installedModels", models.stream().map(m -> m.getName()).filter(n -> n != null).toList());
        if (!installed) {
            result.put("hint", "请先在终端执行：ollama pull " + targetModel);
        }
        return Result.success(result);
    }

    /** 去掉模型名的 tag 部分，如 deepseek-coder:6.7b -> deepseek-coder。 */
    private static String stripTag(String name) {
        int idx = name.indexOf(':');
        return idx > 0 ? name.substring(0, idx) : name;
    }
}
