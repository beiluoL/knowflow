package com.knowflow.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.entity.DocDocument;
import com.knowflow.service.DocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员文档管理")
@RestController
@RequestMapping("/api/admin/docs")
@RequiredArgsConstructor
public class AdminDocController {

    private final DocService docService;

    @Operation(summary = "文档列表")
    @GetMapping
    public Result<PageResult<DocDocument>> list(DocQueryDTO dto) {
        Page<DocDocument> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<DocDocument> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isEmpty()) {
            wrapper.like(DocDocument::getTitle, dto.getKeyword());
        }
        if (dto.getCategoryId() != null) {
            wrapper.eq(DocDocument::getCategoryId, dto.getCategoryId());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(DocDocument::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(DocDocument::getCreateTime);
        Page<DocDocument> result = docService.page(page, wrapper);
        return Result.success(PageResult.of(result));
    }

    @Operation(summary = "文档详情")
    @GetMapping("/{id}")
    public Result<DocDocument> detail(@PathVariable Long id) {
        return Result.success(docService.getById(id));
    }

    @Operation(summary = "新增文档")
    @PostMapping
    public Result<Void> add(@RequestBody DocDocument doc) {
        docService.saveDoc(doc);
        return Result.success();
    }

    @Operation(summary = "更新文档")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DocDocument doc) {
        doc.setId(id);
        docService.updateDoc(doc);
        return Result.success();
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        docService.removeDoc(id);
        return Result.success();
    }
}
