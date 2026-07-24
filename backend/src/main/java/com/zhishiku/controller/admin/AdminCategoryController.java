package com.zhishiku.controller.admin;

import com.zhishiku.common.Result;
import com.zhishiku.entity.DocCategory;
import com.zhishiku.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理员分类管理")
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类列表")
    @GetMapping
    public Result<List<DocCategory>> list() {
        return Result.success(categoryService.list());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<DocCategory> add(@RequestBody DocCategory category) {
        categoryService.save(category);
        return Result.success(category);
    }

    @Operation(summary = "更新分类")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DocCategory category) {
        category.setId(id);
        categoryService.updateById(category);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.removeById(id);
        return Result.success();
    }
}
