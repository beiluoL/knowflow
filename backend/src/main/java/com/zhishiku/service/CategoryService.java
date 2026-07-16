package com.zhishiku.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhishiku.entity.DocCategory;
import com.zhishiku.vo.CategoryVO;

import java.util.List;

public interface CategoryService extends IService<DocCategory> {

    List<CategoryVO> getCategoryTree();

    List<DocCategory> getCategoryList(Long parentId);
}
