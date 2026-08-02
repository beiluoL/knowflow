package com.knowflow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 导入任务取消状态管理。
 * <p>使用 {@link ConcurrentHashMap} 维护每个批次（batchId）的取消标志，
 * Service 在处理每个文件前检查标志，实现「取消正在进行的导入操作」。
 * <ul>
 *   <li>{@link #requestCancel}：由 Controller 的取消接口调用，标记某批次为取消</li>
 *   <li>{@link #isCancelled}：Service 在文件循环中检查，true 时抛出中断</li>
 *   <li>{@link #cleanup}：导入完成（无论成功/失败/取消）后清理标志</li>
 * </ul>
 */
@Slf4j
@Service
public class ImportCancelService {

    /** 已请求取消的批次 ID 集合 */
    private final Set<String> cancelledBatches = ConcurrentHashMap.newKeySet();

    /**
     * 请求取消指定批次的导入。
     *
     * @param batchId 导入批次 ID
     * @return true 表示成功标记取消；false 表示批次已不在进行中（可能已完成）
     */
    public boolean requestCancel(String batchId) {
        if (batchId == null || batchId.isBlank()) return false;
        boolean added = cancelledBatches.add(batchId);
        log.info("请求取消导入批次: batchId={}, 已标记={}", batchId, added);
        return added;
    }

    /**
     * 检查指定批次是否已被请求取消。
     */
    public boolean isCancelled(String batchId) {
        return batchId != null && !batchId.isBlank() && cancelledBatches.contains(batchId);
    }

    /**
     * 清理批次取消标志（导入结束时调用）。
     */
    public void cleanup(String batchId) {
        if (batchId != null) {
            cancelledBatches.remove(batchId);
        }
    }
}
