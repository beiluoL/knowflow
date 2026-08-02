package com.knowflow.service;

import com.knowflow.dto.KnowledgeImportOptionsDTO;
import com.knowflow.vo.KnowledgeImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库目录批量导入服务接口。
 * <p>
 * 支持 Obsidian 仓库 / 本地目录批量导入，核心能力：
 * <ol>
 *     <li>目录层级 → 分类树映射（系统 3 级深度限制，超出转为标签）</li>
 *     <li>文档内图片识别与迁移（Obsidian ![[image]] 与标准 ![](path) 均支持）</li>
 *     <li>Obsidian 链接关系兼容（[[note]] 内部链接、![[image|width]] 图片嵌入）</li>
 *     <li>增量去重（source_path + content_hash，跳过未变更文件）</li>
 *     <li>自动生成标签（目录路径 + 文件名 + front-matter + 正文关键词，可选 AI 智能打标）</li>
 *     <li>富文档解析（PDF/DOC/DOCX/PPT/PPTX/RTF 基于 Apache Tika 提取纯文本）</li>
 * </ol>
 */
public interface KnowledgeImportService {

    /**
     * 批量导入目录文件到知识库（同步，无进度回调）。
     * <p>
     * 前端通过 {@code <input webkitdirectory>} 选择本地目录，将所有文件（含相对路径）上传。
     * 每个文件的 originalFilename 即为相对路径（如 {@code Notes/AI/ML.md}）。
     *
     * @param files   上传的文件数组（markdown / 图片 / 文本 / PDF / DOCX 等）
     * @param options 导入选项（目标知识库、是否创建子分类、是否自动打标等）
     * @param userId  当前操作用户 ID
     * @return 导入结果（成功/跳过/失败计数 + 逐条明细日志 + 新建分类列表）
     */
    KnowledgeImportResultVO importDirectory(MultipartFile[] files,
                                            KnowledgeImportOptionsDTO options,
                                            Long userId);

    /**
     * 批量导入目录文件到知识库（带进度回调，支持取消）。
     * <p>
     * 与 {@link #importDirectory} 的区别：
     * <ul>
     *   <li>处理每个文件前回调 {@link ImportProgressListener#onFileStart}</li>
     *   <li>每个文件处理完成回调 {@link ImportProgressListener#onFileDone}（含状态与说明）</li>
     *   <li>每轮循环检查 {@code batchId} 是否被请求取消，是则提前终止并回调 onCancel</li>
     *   <li>全部完成后回调 {@link ImportProgressListener#onComplete}</li>
     * </ul>
     * listener 为 null 时退化为同步执行（兼容旧调用方）。
     *
     * @param files     上传的文件数组
     * @param options   导入选项
     * @param userId    当前操作用户 ID
     * @param batchId   导入批次 ID（用于取消控制，null 则内部生成）
     * @param listener  进度监听器（可 null）
     * @return 导入结果
     */
    KnowledgeImportResultVO importDirectoryWithProgress(MultipartFile[] files,
                                                         KnowledgeImportOptionsDTO options,
                                                         Long userId,
                                                         String batchId,
                                                         ImportProgressListener listener);
}
