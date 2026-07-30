package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.common.PageResult;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.dto.DocUploadMetaDTO;
import com.knowflow.dto.ReadProgressDTO;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.vo.DocDetailVO;
import com.knowflow.vo.DocVO;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 文档业务服务接口。 */
public interface DocService extends IService<DocDocument> {

    PageResult<DocVO> getDocPage(DocQueryDTO dto);

    DocDetailVO getDocDetail(Long id, Long userId);

    /**
     * 文件型文档上传：服务端抽取正文并入库，返回详情 VO。
     *
     * @param file 原始文件（PDF/Word/PPT 等）
     * @param meta 元信息（标题/分类/标签等）
     * @param userId 当前登录用户 ID
     * @return 上传后的文档详情
     */
    DocDetailVO uploadDoc(MultipartFile file, DocUploadMetaDTO meta, Long userId);

    void toggleFavorite(Long docId, Long userId);

    List<DocVO> getFavoriteList(Long userId);

    List<DocVO> getRecentReadList(Long userId);

    List<DocVO> getRecommendList(Long userId);

    void updateReadProgress(ReadProgressDTO dto, Long userId);

    void saveDoc(DocDocument doc);

    void updateDoc(DocDocument doc);

    void removeDoc(Long id);

    /** 批量删除文档（同步维护分类文档数与关联数据） */
    void batchDeleteDocs(List<Long> ids);

    /** 批量移动文档到目标知识库（更新 categoryId 并维护分类文档数） */
    void batchMoveDocs(List<Long> ids, Long categoryId);

    /** AI 生成文档摘要并回填 doc.summary，返回摘要内容。要求已配置 AI 服务。 */
    String generateAISummary(Long docId);

    /** AI 基于文档内容生成复习闪卡并落库，返回生成的闪卡列表（可指定归属路径/章节）。 */
    List<LearningFlashcard> generateFlashcards(Long docId, Long pathId, Long chapterId);

    /** 查询指定分类（知识库）下的全部有效文档，按 sortOrder 升序，createTime 倒序。 */
    List<DocDocument> listByCategory(Long categoryId);
}
