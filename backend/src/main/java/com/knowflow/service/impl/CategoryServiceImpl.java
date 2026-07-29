package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.service.CategoryService;
import com.knowflow.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 分类业务服务实现。 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<DocCategoryMapper, DocCategory> implements CategoryService {

    private final DocDocumentMapper docDocumentMapper;

    @Override
    public List<CategoryVO> getCategoryTree() {
        List<DocCategory> allCategories = this.list(new LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getStatus, 1)
                .orderByAsc(DocCategory::getSortOrder));
        return buildTree(allCategories);
    }

    @Override
    public List<CategoryVO> getCategoryTreeForAdmin() {
        // 管理端返回全部（含已禁用），便于维护时看到完整结构
        List<DocCategory> allCategories = this.list(new LambdaQueryWrapper<DocCategory>()
                .orderByAsc(DocCategory::getSortOrder));
        return buildTree(allCategories);
    }

    /** 把扁平 List<DocCategory> 组装为树结构。 */
    private List<CategoryVO> buildTree(List<DocCategory> allCategories) {
        List<CategoryVO> allVOs = allCategories.stream()
                .map(cat -> BeanUtil.copyProperties(cat, CategoryVO.class))
                .collect(Collectors.toList());
        Map<Long, List<CategoryVO>> childrenMap = allVOs.stream()
                .filter(vo -> vo.getParentId() != null && vo.getParentId() != 0)
                .collect(Collectors.groupingBy(CategoryVO::getParentId));
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO vo : allVOs) {
            if (vo.getParentId() == null || vo.getParentId() == 0) {
                roots.add(vo);
            }
            List<CategoryVO> children = childrenMap.get(vo.getId());
            if (children != null) {
                vo.setChildren(children);
            }
        }
        return roots;
    }

    @Override
    public void validateParentId(Long currentId, Long parentId) {
        // parentId 为 null 或 0 表示顶级分类
        if (parentId == null || parentId == 0) {
            return;
        }
        // 自环校验
        if (currentId != null && currentId.equals(parentId)) {
            throw new BusinessException("父分类不能是自身");
        }
        DocCategory parent = this.getById(parentId);
        if (parent == null) {
            throw new BusinessException("指定的父分类不存在");
        }
        // 层级深度校验：从 parent 往上数祖先，最多 2 层（parent 算第 1 层，新建/更新后是第 2 层）
        // 即允许：顶级 → 一级子分类 → 二级子分类（共 3 级）
        int depth = 1;
        Long ancestorId = parent.getParentId();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        visited.add(parentId);
        while (ancestorId != null && ancestorId != 0) {
            if (!visited.add(ancestorId)) {
                throw new BusinessException("分类层级存在环，请检查父分类设置");
            }
            depth++;
            if (depth > 2) {
                throw new BusinessException("分类层级最多支持 3 级，无法继续创建子分类");
            }
            DocCategory ancestor = this.getById(ancestorId);
            if (ancestor == null) {
                break;
            }
            ancestorId = ancestor.getParentId();
        }
    }

    @Override
    public List<DocCategory> getCategoryList(Long parentId) {
        return this.list(new LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getParentId, parentId)
                .eq(DocCategory::getStatus, 1)
                .orderByAsc(DocCategory::getSortOrder));
    }

    /** 删除分类：存在子分类或文档时禁止删除，避免脏数据。 */
    @Override
    public void removeCategory(Long id) {
        long childCount = this.count(new LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getParentId, id)
                .eq(DocCategory::getStatus, 1));
        if (childCount > 0) {
            throw new BusinessException("该分类下存在子分类，无法删除");
        }
        long docCount = docDocumentMapper.selectCount(new LambdaQueryWrapper<DocDocument>()
                .eq(DocDocument::getCategoryId, id)
                .eq(DocDocument::getStatus, 1));
        if (docCount > 0) {
            throw new BusinessException("该分类下存在文档，无法删除");
        }
        this.removeById(id);
    }

    @Override
    public void incrementDocCount(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        this.update(new LambdaUpdateWrapper<DocCategory>()
                .eq(DocCategory::getId, categoryId)
                .setSql("doc_count = doc_count + 1"));
    }

    @Override
    public void decrementDocCount(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        this.update(new LambdaUpdateWrapper<DocCategory>()
                .eq(DocCategory::getId, categoryId)
                .setSql("doc_count = GREATEST(0, doc_count - 1)"));
    }
}
