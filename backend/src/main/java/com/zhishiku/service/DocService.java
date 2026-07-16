package com.zhishiku.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhishiku.common.PageResult;
import com.zhishiku.dto.DocQueryDTO;
import com.zhishiku.dto.ReadProgressDTO;
import com.zhishiku.entity.DocDocument;
import com.zhishiku.vo.DocDetailVO;
import com.zhishiku.vo.DocVO;

import java.util.List;

public interface DocService extends IService<DocDocument> {

    PageResult<DocVO> getDocPage(DocQueryDTO dto);

    DocDetailVO getDocDetail(Long id, Long userId);

    void toggleFavorite(Long docId, Long userId);

    List<DocVO> getFavoriteList(Long userId);

    List<DocVO> getRecentReadList(Long userId);

    List<DocVO> getRecommendList(Long userId);

    void updateReadProgress(ReadProgressDTO dto, Long userId);
}
