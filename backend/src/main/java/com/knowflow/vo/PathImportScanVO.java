package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径导入：目录扫描结果 VO。
 * <p>
 * 用户输入路径（绝对/相对）后，后端解析并扫描该路径下所有可导入文件，
 * 返回此 VO 供前端预览确认。确认后前端调用 {@code /path/stream} 发起实际导入。
 * <p>
 * 与 {@link LocalReaderScanVO} 的区别：
 * <ul>
 *     <li>LocalReaderScanVO：面向本地阅读，仅含 Markdown + 代码文件，返回目录树结构</li>
 *     <li>PathImportScanVO：面向路径导入，含富文档 + 图片，返回扁平文件列表（含大小、类型）</li>
 * </ul>
 */
@Data
public class PathImportScanVO {

    /** 解析后的绝对路径（规范化后）。 */
    private String absolutePath;

    /** 根目录/文件名。 */
    private String rootName;

    /** 是否为单文件模式（true=用户输入的是文件路径，false=目录路径）。 */
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    private boolean isFile;

    public boolean isFile() { return isFile; }
    public void setFile(boolean isFile) { this.isFile = isFile; }

    /** 文档数量（Markdown + 代码 + 富文档）。 */
    private int docCount;

    /** 图片数量。 */
    private int imageCount;

    /** 目录数量（单文件模式下为 0）。 */
    private int dirCount;

    /** 待导入文件列表（扁平结构，按路径排序）。 */
    private List<FileEntry> files = new ArrayList<>();

    /**
     * 单个文件条目。
     */
    @Data
    public static class FileEntry {
        /** 文件名（含扩展名）。 */
        private String name;
        /** 相对根目录的路径（如 Notes/AI/ML.md）；单文件模式下即文件名。 */
        private String path;
        /** 文件类型：doc / image。 */
        private String type;
        /** 扩展名（小写，不含点），用于前端图标显示。 */
        private String ext;
        /** 文件大小（字节）。 */
        private long size;
    }
}
