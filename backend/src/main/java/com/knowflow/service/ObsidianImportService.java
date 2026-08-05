package com.knowflow.service;

import com.knowflow.dto.ObsidianImportDTO;
import com.knowflow.vo.ObsidianImportResultVO;
import com.knowflow.vo.PathImportScanVO;

import java.util.List;

/**
 * Obsidian 目录一键导入服务：扫描本地目录 → 自动生成四模块（知识库/学习路径/闪卡/题库）。
 * <p>
 * 设计原则：不修改既有知识库导入核心逻辑，复用 {@link KnowledgeImportService} 与
 * {@link PathImportService} 完成知识库导入，再基于导入结果编排其余三个模块的自动生成。
 * 图片在导入后由本服务统一兜底迁移，确保四模块均能通过 /uploads 正确引用。
 */
public interface ObsidianImportService {

    /**
     * 一键导入并生成所选模块（同步，无进度推送）。
     *
     * @param dto    请求参数（路径、目标知识库、模块列表等）
     * @param userId 当前操作用户 ID
     * @return 导入结果（各模块统计 + 新 ID）
     */
    ObsidianImportResultVO importAll(ObsidianImportDTO dto, Long userId);

    /**
     * 带进度推送的一键导入（用于 SSE 流式进度）。
     *
     * @param dto      请求参数
     * @param userId   当前操作用户 ID
     * @param batchId  导入批次 ID（用于取消控制）
     * @param listener 进度监听器（可 null，为 null 时退化为同步执行）
     * @return 导入结果
     */
    ObsidianImportResultVO importAllWithProgress(ObsidianImportDTO dto, Long userId,
                                                 String batchId, ImportProgressListener listener);

    /**
     * 预览指定的若干文件（文件选择导入方式），返回其结构信息。
     *
     * @param filePaths 文件绝对路径列表
     * @return 扫描结果 VO（仅包含所选文件）
     */
    PathImportScanVO scanFiles(List<String> filePaths);
}
