package com.knowflow.service;

import com.knowflow.vo.KnowledgeImportResultVO;

/**
 * 知识库导入进度监听器。
 * <p>由 SSE 控制器实现，Service 在处理每个文件的关键节点回调对应方法，
 * 实现实时进度推送。所有方法均为 best-effort：回调失败不影响导入主流程。
 */
public interface ImportProgressListener {

    /**
     * 导入开始：推送总文件数与批次 ID。
     *
     * @param batchId  导入批次 ID（用于取消）
     * @param total    待处理文档总数
     */
    void onStart(String batchId, int total);

    /**
     * 单个文件开始处理。
     *
     * @param index    当前文件序号（1-based）
     * @param total    文件总数
     * @param relPath  文件相对路径
     */
    void onFileStart(int index, int total, String relPath);

    /**
     * 单个文件处理完成（成功/跳过/失败均会回调）。
     *
     * @param index    当前文件序号
     * @param total    文件总数
     * @param relPath  文件相对路径
     * @param status   处理结果：success / skipped / failed
     * @param message  说明信息（跳过原因 / 失败原因 / 成功提示）
     */
    void onFileDone(int index, int total, String relPath, String status, String message);

    /**
     * 导入完成：推送最终汇总结果。
     *
     * @param result 导入结果 VO
     */
    void onComplete(KnowledgeImportResultVO result);

    /**
     * 导入被取消：用户主动取消或服务端中断。
     *
     * @param reason 取消原因
     */
    void onCancel(String reason);

    /**
     * 导入异常：非预期错误。
     *
     * @param error 错误信息
     */
    void onError(String error);
}
