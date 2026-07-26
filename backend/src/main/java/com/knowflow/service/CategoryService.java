package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.DocCategory;
import com.knowflow.vo.CategoryVO;

import java.util.List;

public interface CategoryService extends IService<DocCategory> {

    List<CategoryVO> getCategoryTree();

    List<DocCategory> getCategoryList(Long parentId);

    void removeCategory(Long id);

    void incrementDocCount(Long categoryId);

    void decrementDocCount(Long categoryId);
}
