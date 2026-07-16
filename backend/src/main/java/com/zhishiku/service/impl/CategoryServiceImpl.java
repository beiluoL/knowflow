package com.zhishiku.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhishiku.entity.DocCategory;
import com.zhishiku.mapper.DocCategoryMapper;
import com.zhishiku.service.CategoryService;
import com.zhishiku.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<DocCategoryMapper, DocCategory> implements CategoryService {

    @Override
    public List<CategoryVO> getCategoryTree() {
        List<DocCategory> allCategories = this.list(new LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getStatus, 1)
                .orderByAsc(DocCategory::getSortOrder));
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
    public List<DocCategory> getCategoryList(Long parentId) {
        return this.list(new LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getParentId, parentId)
                .eq(DocCategory::getStatus, 1)
                .orderByAsc(DocCategory::getSortOrder));
    }
}
