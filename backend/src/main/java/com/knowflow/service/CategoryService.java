package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.DocCategory;
import com.knowflow.vo.CategoryVO;

import java.util.List;

/** 分类业务服务接口。 */
public interface CategoryService extends IService<DocCategory> {

    List<CategoryVO> getCategoryTree();

    /** 管理端树：与 getCategoryTree 类似，但不按 status 过滤（管理端可见全部）。 */
    List<CategoryVO> getCategoryTreeForAdmin();

    List<DocCategory> getCategoryList(Long parentId);

    void removeCategory(Long id);

    void incrementDocCount(Long categoryId);

    void decrementDocCount(Long categoryId);

    /** 校验 parentId 合法性：父分类存在、非自环、不形成环、层级深度 ≤ 3。 */
    void validateParentId(Long currentId, Long parentId);
}
