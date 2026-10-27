package com.knowflow.controller;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.dto.DocUploadMetaDTO;
import com.knowflow.dto.ReadProgressDTO;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.DocService;
import com.knowflow.vo.DocDetailVO;
import com.knowflow.vo.DocVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 文档管理 REST 接口，提供文档查询、收藏、阅读进度等能力（部分接口允许匿名访问）。 */
@Tag(name = "文档接口")
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取当前登录用户 ID；未登录（匿名 / token 无效）时抛出 401 业务异常。
     *
     * <p>实现模式与 P1-A 第一/二/三批（{@code ChatController} / {@code LearningController} /
     * {@code StudyGroupController}）完全一致，复用 {@link SecurityUtils#getCurrentUserIdNullable()}，
     * 不新建任何鉴权机制。用双参 {@code BusinessException(401, ...)} 而非单参构造，
     * 以保证 HTTP 状态码与 body.code 同时为 401（单参默认 code=500 会回落 HTTP 200）。</p>
     */
    private static Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserIdNullable();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }

    @Operation(summary = "文档列表")
    @GetMapping
    public Result<PageResult<DocVO>> list(DocQueryDTO dto) {
        return Result.success(docService.getDocPage(dto));
    }

    @Operation(summary = "文档详情")
    @GetMapping("/{id}")
    public Result<DocDetailVO> detail(@PathVariable Long id) {
        // GET /api/docs/{id} 位于 SecurityConfig permitAll 白名单：匿名可读。
        // userId 仅用于「是否已收藏」等个性化字段，允许为空，故使用 nullable 而非 currentUserId()。
        return Result.success(docService.getDocDetail(id, SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "收藏/取消收藏")
    @PostMapping("/{id}/favorite")
    public Result<Void> toggleFavorite(@PathVariable Long id) {
        docService.toggleFavorite(id, currentUserId());
        return Result.success();
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping("/favorites")
    public Result<List<DocVO>> favorites() {
        // 与 detail 一致采用 nullable 语义：未登录时 Service 侧按空用户过滤得到空列表，不产生 401 行为变更。
        return Result.success(docService.getFavoriteList(SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "最近阅读")
    @GetMapping("/recent")
    public Result<List<DocVO>> recent() {
        // 未登录时返回空列表；身份只能来自 SecurityContext，不接受任何客户端 userId 入参。
        return Result.success(docService.getRecentReadList(SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "推荐文档")
    @GetMapping("/recommend")
    public Result<List<DocVO>> recommend() {
        // GET /api/docs/recommend 位于 permitAll 白名单：匿名可读（userId 仅作可选上下文传入）。
        return Result.success(docService.getRecommendList(SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "更新阅读进度")
    @PostMapping("/progress")
    public Result<Void> updateProgress(@RequestBody @Valid ReadProgressDTO dto) {
        docService.updateReadProgress(dto, currentUserId());
        return Result.success();
    }

    @Operation(summary = "上传/创建文档")
    @PostMapping
    public Result<DocDetailVO> create(@RequestBody DocDocument doc) {
        // 先解析身份再落库，保持原「守卫在最前」的执行顺序。
        // doc_document 无 owner 列（全局共享资源），不收任何客户端 userId，故不存在归属伪造面。
        Long userId = currentUserId();
        docService.saveDoc(doc);
        return Result.success(docService.getDocDetail(doc.getId(), userId));
    }

    @Operation(summary = "上传文件型文档（PDF/Word/PPT 等），服务端抽取正文入库并支持原文下载")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocDetailVO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("meta") String metaJson) {
        Long userId = currentUserId();
        DocUploadMetaDTO meta;
        try {
            meta = objectMapper.readValue(metaJson, DocUploadMetaDTO.class);
        } catch (Exception e) {
            throw new BusinessException(400, "文档元信息格式错误");
        }
        DocDetailVO vo = docService.uploadDoc(file, meta, userId);
        return Result.success(vo);
    }

    @Operation(summary = "更新文档")
    @PutMapping("/{id}")
    public Result<DocDetailVO> update(@PathVariable Long id, @RequestBody DocDocument doc) {
        Long userId = currentUserId();
        doc.setId(id);
        docService.updateDoc(doc);
        return Result.success(docService.getDocDetail(id, userId));
    }

    @Operation(summary = "AI 生成文档摘要")
    @PostMapping("/{id}/ai-summary")
    public Result<String> generateSummary(@PathVariable Long id) {
        // 保留该局部变量：原实现即「取出未使用」的 dead assignment，
        // 本轮仅替换 principal 获取方式，不顺手删除（详见 AGENT_HANDOFF P1-A 第四批）。
        Long userId = currentUserId();
        return Result.success(docService.generateAISummary(id));
    }

    @Operation(summary = "AI 基于文档生成闪卡")
    @PostMapping("/{id}/ai-flashcards")
    public Result<List<LearningFlashcard>> generateFlashcards(@PathVariable Long id,
                                                              @RequestParam(required = false) Long pathId,
                                                              @RequestParam(required = false) Long chapterId) {
        // P0-1b：userId 必须一路透传到 Service —— learning_flashcard.user_id 为 NOT NULL，
        // 且该表按设计即「用户私有闪卡」，生成的卡归属当前登录用户（不由前端传参）。
        Long userId = currentUserId();
        return Result.success(docService.generateFlashcards(id, pathId, chapterId, userId));
    }
}
