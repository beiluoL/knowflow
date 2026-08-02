package com.knowflow.vo;

import lombok.Data;

/**
 * 上传文件信息 VO（用于文件管理列表）。
 */
@Data
public class UploadFileVO {
    /** 文件名（UUID 生成后的存储名） */
    private String fileName;
    /** 相对访问 URL，如 /uploads/2026/08/02/xxx.png */
    private String fileUrl;
    /** 相对于上传根目录的路径，如 2026/08/02/xxx.png */
    private String relativePath;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 文件扩展名（如 png、pdf） */
    private String extension;
    /** 最后修改时间（毫秒时间戳） */
    private Long lastModified;
    /** 是否为图片 */
    private Boolean isImage;
}
