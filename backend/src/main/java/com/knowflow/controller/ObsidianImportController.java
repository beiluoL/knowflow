package com.knowflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.ObsidianImportDTO;
import com.knowflow.service.ImportProgressListener;
import com.knowflow.service.KbMemberService;
import com.knowflow.service.ObsidianImportService;
import com.knowflow.service.PathImportService;
import com.knowflow.vo.ObsidianImportResultVO;
import com.knowflow.vo.PathImportScanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Obsidian 目录一键导入接口：扫描本地目录 → 自动生成四模块（知识库/学习路径/闪卡/题库）。
 * <p>支持前端选择目录、输入绝对/相对路径、或指定若干文件导入；支持 SSE 流式进度。
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 异步执行导入线程池（大目录解析可能较慢）。 */
    private final ExecutorService importExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "obsidian-import");
        t.setDaemon(true);
        return t;
    });

    @Operation(summary = "扫描本地路径，返回待导入的 Markdown 文件结构（预览）")
    @GetMapping("/scan")
    public Result<PathImportScanVO> scan(
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "relativeTo", required = false) String relativeTo,
            @RequestParam(value = "filePaths", required = false) List<String> filePaths) {
        PathImportScanVO vo;
        if (filePaths != null && !filePaths.isEmpty()) {
            vo = obsidianImportService.scanFiles(filePaths);
        } else {
            if (path == null || path.isBlank()) {
                return Result.error("请提供 path 或 filePaths");
            }
            String abs = pathImportService.resolvePath(path, relativeTo);
            vo = pathImportService.scanForImport(abs);
        }
        return Result.success(vo);
    }

    @Operation(summary = "一键导入并生成所选模块（同步，无进度）")
    @PostMapping("/generate")
    public Result<ObsidianImportResultVO> generate(@RequestBody ObsidianImportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (dto.getTargetCategoryId() != null
                && !kbMemberService.canEditDocs(dto.getTargetCategoryId(), userId)) {
            return Result.error("无权向该知识库导入文档（需 Owner 或 Editor 权限）");
        }
        if (dto.getModules() == null || dto.getModules().isEmpty()) {
            dto.setModules(List.of("knowledge", "path", "flashcard", "quiz"));
        }
        ObsidianImportResultVO result = obsidianImportService.importAll(dto, userId);
        return Result.success("导入完成", result);
    }

    @Operation(summary = "一键导入（SSE 流式进度）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter importStream(@RequestBody ObsidianImportDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 预生成 batchId，随进度事件回传前端
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        if (dto.getTargetCategoryId() != null
                && !kbMemberService.canEditDocs(dto.getTargetCategoryId(), userId)) {
            SseEmitter em = new SseEmitter(60_000L);
            sendEvent(em, "error", Map.of("error", "无权向该知识库导入文档（需 Owner 或 Editor 权限）"));
            try { em.complete(); } catch (Exception ignored) {}
            return em;
        }
        if (dto.getModules() == null || dto.getModules().isEmpty()) {
            dto.setModules(List.of("knowledge", "path", "flashcard", "quiz"));
        }

        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        ImportProgressListener listener = buildListener(emitter, batchId);

        log.info("Obsidian 目录导入(SSE): userId={}, batchId={}", userId, batchId);
        importExecutor.submit(() -> {
            try {
                ObsidianImportResultVO result =
                        obsidianImportService.importAllWithProgress(dto, userId, batchId, listener);
                sendEvent(emitter, "complete", result);
                emitter.complete();
            } catch (Exception e) {
                log.error("Obsidian 导入异步任务异常: batchId={}", batchId, e);
                listener.onError("导入异常：" + e.getMessage());
            }
        });

        emitter.onCompletion(() -> log.debug("SSE 连接完成: batchId={}", batchId));
        emitter.onTimeout(() -> log.warn("SSE 连接超时: batchId={}", batchId));
        return emitter;
    }

    /** 构造进度监听器：将导入各阶段回调映射为 SSE 事件推送。 */
    private ImportProgressListener buildListener(SseEmitter emitter, String batchId) {
        return new ImportProgressListener() {
            @Override
            public void onStart(String bid, int total) {
                sendEvent(emitter, "start", Map.of("batchId", bid, "total", total));
            }

            @Override
            public void onFileStart(int index, int total, String relPath) {
                sendEvent(emitter, "fileStart", Map.of(
                        "index", index, "total", total, "path", relPath));
            }

            @Override
            public void onFileDone(int index, int total, String relPath, String status, String message) {
                Map<String, Object> data = new java.util.HashMap<>();
                data.put("index", index);
                data.put("total", total);
                data.put("path", relPath);
                data.put("status", status);
                data.put("message", message);
                sendEvent(emitter, "fileDone", data);
            }

            @Override
            public void onComplete(com.knowflow.vo.KnowledgeImportResultVO result) {
                // 真正的汇总结果由控制器在 importAllWithProgress 返回后通过 complete 事件推送，
                // 此处监听器占位即可。
            }

            @Override
            public void onCancel(String reason) {
                sendEvent(emitter, "cancel", Map.of("reason", reason));
                try { emitter.complete(); } catch (Exception ignored) {}
            }

            @Override
            public void onError(String error) {
                sendEvent(emitter, "error", Map.of("error", error));
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        };
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: event={}, err={}", eventName, e.getMessage());
        }
    }
}
