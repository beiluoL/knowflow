package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.common.PageResult;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.dto.ReadProgressDTO;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.vo.DocDetailVO;
import com.knowflow.vo.DocVO;

import java.util.List;

/** 文档业务服务接口。 */
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

    /** AI 生成文档摘要并回填 doc.summary，返回摘要内容。要求已配置 AI 服务。 */
    String generateAISummary(Long docId);

    /** AI 基于文档内容生成复习闪卡并落库，返回生成的闪卡列表（可指定归属路径/章节）。 */
    List<LearningFlashcard> generateFlashcards(Long docId, Long pathId, Long chapterId);
}
