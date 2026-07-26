package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.common.PageResult;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.dto.ReadProgressDTO;
import com.knowflow.entity.DocDocument;
import com.knowflow.vo.DocDetailVO;
import com.knowflow.vo.DocVO;

import java.util.List;

public interface DocService extends IService<DocDocument> {

    PageResult<DocVO> getDocPage(DocQueryDTO dto);

    DocDetailVO getDocDetail(Long id, Long userId);

    void toggleFavorite(Long docId, Long userId);

    List<DocVO> getFavoriteList(Long userId);

    List<DocVO> getRecentReadList(Long userId);

    List<DocVO> getRecommendList(Long userId);

    void updateReadProgress(ReadProgressDTO dto, Long userId);

    void saveDoc(DocDocument doc);

    void updateDoc(DocDocument doc);

    void removeDoc(Long id);
}
