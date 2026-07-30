package com.knowflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作区文件元信息（SC1-IDE-02 可重置实验沙箱）。
 * 列表接口直接返回文件内容，便于前端打开即编辑（学习沙箱文件体量小，可接受）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeWorkspaceFileVO {
    /** 相对工作区根目录的路径（同时也是文件名，单层结构） */
    private String path;

    /** 文件名（path 的末段） */
    private String name;

    /** 文件字节大小 */
    private long size;

    /** 由扩展名推断的语言标识（python/java/javascript/cpp 等），用于编辑器与运行判定 */
    private String language;

    /** 文件文本内容 */
    private String content;
}
