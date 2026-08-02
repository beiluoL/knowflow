package com.knowflow.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.KnowledgeImportOptionsDTO;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.KbMember;
import com.knowflow.service.CategoryService;
import com.knowflow.service.ImportCancelService;
import com.knowflow.service.ImportProgressListener;
import com.knowflow.service.KbMemberService;
import com.knowflow.service.KnowledgeImportService;
import com.knowflow.service.PathImportService;
import com.knowflow.vo.CategoryVO;
import com.knowflow.vo.KnowledgeImportResultVO;
import com.knowflow.vo.PathImportScanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 知识库目录批量导入接口。
 * <p>
 * 支持 Obsidian 仓库 / 本地目录批量导入：
 * <ul>
 *     <li>前端通过 {@code <input webkitdirectory>} 选择目录，上传所有文件</li>
 *     <li>每个文件的 originalFilename 为相对路径（如 Notes/AI/ML.md）</li>
 *     <li>后端自动解析目录层级→分类树、迁移图片、重写链接、生成标签、增量去重</li>
 *     <li>支持 PDF/DOC/DOCX/PPT/PPTX/RTF 富文档（基于 Apache Tika 提取文本）</li>
 *     <li>提供 SSE 流式接口实时推送导入进度，支持取消</li>
 * </ul>
 */
@Slf4j
@Tag(name = "知识库导入接口")
@RestController
@RequestMapping("/api/knowledge/import")
@RequiredArgsConstructor
public class KnowledgeImportController {

    private final KnowledgeImportService knowledgeImportService;
    private final ImportCancelService importCancelService;
    private final ObjectMapper objectMapper;
    private final CategoryService categoryService;
    private final KbMemberService kbMemberService;
    private final PathImportService pathImportService;

    /** 异步执行导入任务的线程池（与 SseEmitter 配合，不阻塞请求线程） */
    private final ExecutorService importExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "import-stream");
        t.setDaemon(true);
        return t;
    });

    /**
     * 批量导入目录文件到知识库（同步，无进度）。
     * <p>保留兼容旧调用方；新前端建议改用 {@link #importDirectoryStream} 获取实时进度。
     */
    @Operation(summary = "批量导入目录到知识库（同步）")
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

        KnowledgeImportOptionsDTO options = buildOptions(targetCategoryId, createSubCategories,
                autoTags, aiTags, incremental, maxContentChars);

        log.info("知识库目录导入(同步): userId={}, targetCategoryId={}, fileCount={}",
                userId, targetCategoryId, files != null ? files.length : 0);

        KnowledgeImportResultVO result = knowledgeImportService.importDirectory(files, options, userId);
        return Result.success("导入完成", result);
    }

    /**
     * 批量导入目录文件到知识库（SSE 流式推送进度）。
     * <p>
     * 响应类型为 {@code text/event-stream}，前端通过 EventSource 监听。
     * 推送事件类型：
     * <ul>
     *   <li>{@code start}：{ batchId, total }</li>
     *   <li>{@code fileStart}：{ index, total, path }</li>
     *   <li>{@code fileDone}：{ index, total, path, status, message }</li>
     *   <li>{@code complete}：KnowledgeImportResultVO 完整结果</li>
     *   <li>{@code cancel}：{ reason }</li>
     *   <li>{@code error}：{ error }</li>
     * </ul>
     * 客户端可调用 {@link #cancelImport} 取消导入。
     */
    @Operation(summary = "批量导入目录到知识库（SSE 流式进度）")
    @PostMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter importDirectoryStream(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("targetCategoryId") Long targetCategoryId,
            @RequestParam(value = "createSubCategories", defaultValue = "true") Boolean createSubCategories,
            @RequestParam(value = "autoTags", defaultValue = "true") Boolean autoTags,
            @RequestParam(value = "aiTags", defaultValue = "false") Boolean aiTags,
            @RequestParam(value = "incremental", defaultValue = "true") Boolean incremental,
            @RequestParam(value = "maxContentChars", defaultValue = "50000") Integer maxContentChars) {

        Long userId = SecurityUtils.getCurrentUserId();
        // 预生成 batchId，随 start 事件回传前端，后续取消接口需要
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        KnowledgeImportOptionsDTO options = buildOptions(targetCategoryId, createSubCategories,
                autoTags, aiTags, incremental, maxContentChars);

        // 超时设 10 分钟（大目录 + AI 打标可能较慢）
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        // 主线程中提前做权限校验：异步线程中 SecurityContextHolder 默认不传递，
        // 会导致 SecurityUtils.isAdmin() 失效，admin 用户被误判为无权导入。
        if (!kbMemberService.canEditDocs(targetCategoryId, userId)) {
            sendEvent(emitter, "error", Map.of(
                    "error", "无权向该知识库导入文档（需 Owner 或 Editor 权限）"));
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            return emitter;
        }

        log.info("知识库目录导入(SSE): userId={}, targetCategoryId={}, fileCount={}, batchId={}",
                userId, targetCategoryId, files != null ? files.length : 0, batchId);

        // 构造进度监听器：每个回调通过 SSE 推送对应事件
        ImportProgressListener listener = buildListener(emitter);

        // 异步执行导入，避免阻塞 Servlet 线程
        importExecutor.submit(() -> {
            try {
                knowledgeImportService.importDirectoryWithProgress(
                        files, options, userId, batchId, listener);
            } catch (Exception e) {
                log.error("导入异步任务异常: batchId={}", batchId, e);
                listener.onError("导入异常：" + e.getMessage());
            }
        });

        // 客户端断开连接时的回调
        emitter.onCompletion(() -> log.debug("SSE 连接完成: batchId={}", batchId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: batchId={}", batchId);
            importCancelService.requestCancel(batchId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.warn("SSE 连接错误: batchId={}", batchId, throwable);
            importCancelService.requestCancel(batchId);
        });

        return emitter;
    }

    /**
     * 取消正在进行的导入任务。
     *
     * @param batchId 导入批次 ID（由 start 事件返回）
     */
    @Operation(summary = "取消导入任务")
    @PostMapping("/cancel")
    public Result<Void> cancelImport(@RequestParam("batchId") String batchId) {
        boolean ok = importCancelService.requestCancel(batchId);
        return Result.success(ok ? "已请求取消" : "批次不存在或已完成", null);
    }

    // ==================== 路径导入 ====================

    /**
     * 路径解析 + 扫描：校验路径有效性并返回待导入文件列表。
     * <p>
     * 用户输入路径后先调用此接口预览，确认后再调用 {@link #importPathStream} 发起实际导入。
     * 支持绝对路径（如 {@code /home/user/docs/}）和相对路径（如 {@code ./src/}），
     * 也支持单文件路径（如 {@code /home/user/notes.md}）。
     *
     * @param path       用户输入的路径
     * @param relativeTo 相对基准（可选，用于相对路径解析；不传则基于 JVM 工作目录）
     * @return 扫描结果（含文档/图片/目录数量 + 扁平文件列表）
     */
    @Operation(summary = "路径导入：扫描路径返回待导入文件列表")
    @PostMapping("/path/scan")
    public Result<PathImportScanVO> scanPath(
            @RequestParam("path") String path,
            @RequestParam(value = "relativeTo", required = false) String relativeTo) {
        String absolutePath = pathImportService.resolvePath(path, relativeTo);
        PathImportScanVO vo = pathImportService.scanForImport(absolutePath);
        return Result.success(vo);
    }

    /**
     * 路径导入：SSE 流式推送导入进度。
     * <p>
     * 内部复用 {@link KnowledgeImportService#importDirectoryWithProgress}，
     * 将本地文件读取为字节后包装为 MultipartFile 走现有导入流程，
     * 因此进度回调、增量去重、图片迁移、标签生成等逻辑完全一致。
     * <p>
     * 与 {@link #importDirectoryStream} 的区别：前者通过 multipart 上传文件，
     * 本接口通过服务端直接读取本地文件，避免大目录 HTTP 上传开销。
     * 推送事件类型与 {@code /stream} 完全一致。
     *
     * @param path              已校验的绝对路径（由 {@link #scanPath} 返回的 absolutePath）
     * @param targetCategoryId  目标知识库 ID
     * @param createSubCategories 是否按目录创建子分类
     * @param autoTags          是否自动生成标签
     * @param aiTags            是否启用 AI 智能打标
     * @param incremental       是否启用增量去重
     * @param maxContentChars   单篇正文最大字符数
     */
    @Operation(summary = "路径导入：通过本地路径导入目录（SSE 流式进度）")
    @PostMapping(value = "/path/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter importPathStream(
            @RequestParam("path") String path,
            @RequestParam("targetCategoryId") Long targetCategoryId,
            @RequestParam(value = "createSubCategories", defaultValue = "true") Boolean createSubCategories,
            @RequestParam(value = "autoTags", defaultValue = "true") Boolean autoTags,
            @RequestParam(value = "aiTags", defaultValue = "false") Boolean aiTags,
            @RequestParam(value = "incremental", defaultValue = "true") Boolean incremental,
            @RequestParam(value = "maxContentChars", defaultValue = "50000") Integer maxContentChars) {

        Long userId = SecurityUtils.getCurrentUserId();
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        KnowledgeImportOptionsDTO options = buildOptions(targetCategoryId, createSubCategories,
                autoTags, aiTags, incremental, maxContentChars);
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        // 主线程提前做权限校验（与 /stream 一致）
        if (!kbMemberService.canEditDocs(targetCategoryId, userId)) {
            sendEvent(emitter, "error", Map.of(
                    "error", "无权向该知识库导入文档（需 Owner 或 Editor 权限）"));
            try { emitter.complete(); } catch (Exception ignored) {}
            return emitter;
        }

        // 主线程提前扫描文件，避免异步线程中路径异常难以反馈
        MultipartFile[] files;
        try {
            String absolutePath = pathImportService.resolvePath(path, null);
            files = pathImportService.collectFiles(absolutePath);
        } catch (Exception e) {
            log.warn("路径导入扫描失败: path={}, error={}", path, e.getMessage());
            sendEvent(emitter, "error", Map.of("error", "路径扫描失败：" + e.getMessage()));
            try { emitter.complete(); } catch (Exception ignored) {}
            return emitter;
        }
        if (files == null || files.length == 0) {
            sendEvent(emitter, "error", Map.of("error", "路径下未找到可导入的文件"));
            try { emitter.complete(); } catch (Exception ignored) {}
            return emitter;
        }

        log.info("路径导入(SSE): userId={}, path={}, targetCategoryId={}, fileCount={}, batchId={}",
                userId, path, targetCategoryId, files.length, batchId);

        // 复用与 /stream 相同的进度监听器构造
        ImportProgressListener listener = buildListener(emitter);

        // 异步执行导入
        importExecutor.submit(() -> {
            try {
                knowledgeImportService.importDirectoryWithProgress(
                        files, options, userId, batchId, listener);
            } catch (Exception e) {
                log.error("路径导入异步任务异常: batchId={}", batchId, e);
                listener.onError("导入异常：" + e.getMessage());
            }
        });

        emitter.onCompletion(() -> log.debug("路径导入 SSE 连接完成: batchId={}", batchId));
        emitter.onTimeout(() -> {
            log.warn("路径导入 SSE 连接超时: batchId={}", batchId);
            importCancelService.requestCancel(batchId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.warn("路径导入 SSE 连接错误: batchId={}", batchId, throwable);
            importCancelService.requestCancel(batchId);
        });

        return emitter;
    }

    /**
     * 查询当前用户可向其导入文档的知识库列表（顶级分类）。
     * <p>
     * 仅返回用户具有 OWNER / EDITOR 角色（含系统 ADMIN）的顶级分类，
     * 避免前端展示了无权导入的知识库后，在导入提交时才被后端拒绝。
     *
     * @return 可导入的知识库列表（仅顶级分类，扁平结构）
     */
    @Operation(summary = "查询当前用户可导入的知识库列表")
    @GetMapping("/editable-kbs")
    public Result<List<CategoryVO>> listEditableKbs() {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        List<DocCategory> categories;
        if (isAdmin) {
            // 系统管理员：返回全部顶级启用的知识库
            categories = categoryService.list(new LambdaQueryWrapper<DocCategory>()
                    .and(w -> w.isNull(DocCategory::getParentId).or().eq(DocCategory::getParentId, 0))
                    .eq(DocCategory::getStatus, 1)
                    .orderByAsc(DocCategory::getSortOrder));
        } else {
            // 普通用户：先查 KbMember 中 OWNER/EDITOR 角色的关联记录
            List<KbMember> members = kbMemberService.list(new LambdaQueryWrapper<KbMember>()
                    .eq(KbMember::getUserId, userId)
                    .eq(KbMember::getStatus, KbMember.STATUS_ACTIVE)
                    .in(KbMember::getRole, KbMember.ROLE_OWNER, KbMember.ROLE_EDITOR));
            if (members.isEmpty()) {
                return Result.success(new ArrayList<>());
            }
            List<Long> categoryIds = members.stream()
                    .map(KbMember::getCategoryId)
                    .distinct()
                    .collect(Collectors.toList());
            // 取对应分类，仅保留顶级启用项（防御性：成员配置异常指向子分类时直接排除）
            categories = categoryService.listByIds(categoryIds).stream()
                    .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                    .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                    .sorted(Comparator.comparingInt(c -> c.getSortOrder() == null ? 0 : c.getSortOrder()))
                    .collect(Collectors.toList());
        }

        List<CategoryVO> vos = categories.stream().map(cat -> {
            CategoryVO vo = new CategoryVO();
            BeanUtil.copyProperties(cat, vo);
            return vo;
        }).collect(Collectors.toList());
        return Result.success(vos);
    }

    // ==================== 内部工具 ====================

    /**
     * 构造 SSE 进度监听器：将导入进度事件转换为 SSE 事件推送给前端。
     * <p>
     * {@code /stream} 和 {@code /path/stream} 共用此监听器，保证事件格式一致。
     */
    private ImportProgressListener buildListener(SseEmitter emitter) {
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
                Map<String, Object> data = new HashMap<>();
                data.put("index", index);
                data.put("total", total);
                data.put("path", relPath);
                data.put("status", status);
                data.put("message", message);
                sendEvent(emitter, "fileDone", data);
            }

            @Override
            public void onComplete(KnowledgeImportResultVO result) {
                sendEvent(emitter, "complete", result);
                try { emitter.complete(); } catch (Exception ignored) {}
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

    private KnowledgeImportOptionsDTO buildOptions(Long targetCategoryId, Boolean createSubCategories,
                                                    Boolean autoTags, Boolean aiTags,
                                                    Boolean incremental, Integer maxContentChars) {
        KnowledgeImportOptionsDTO options = new KnowledgeImportOptionsDTO();
        options.setTargetCategoryId(targetCategoryId);
        options.setCreateSubCategories(createSubCategories);
        options.setAutoTags(autoTags);
        options.setAiTags(aiTags);
        options.setIncremental(incremental);
        options.setMaxContentChars(maxContentChars);
        return options;
    }

    /**
     * 发送 SSE 事件：data 字段为 JSON 字符串。
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开或连接已关闭，忽略
            log.debug("SSE 发送失败（客户端可能已断开）: event={}, err={}", eventName, e.getMessage());
        }
    }
}
