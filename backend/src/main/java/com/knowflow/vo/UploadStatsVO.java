package com.knowflow.vo;

import lombok.Data;

/**
 * 上传存储统计 VO。
 */
@Data
public class UploadStatsVO {
    /** 当前上传根目录（绝对路径） */
    private String uploadDir;
    /** 文件总数 */
    private Long totalFiles;
    /** 总占用大小（字节） */
    private Long totalSize;
    /** 总占用大小（人类可读，如 12.3 MB） */
    private String totalSizeReadable;
    /** 图片文件数 */
    private Long imageCount;
    /** 其他文件数 */
    private Long otherCount;
}
