package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地阅读器：目录扫描结果 VO。
 * <p>
 * 返回指定路径下的目录树结构（仅 Markdown 文档与子目录），
 * 供前端构建目录树展示。
 */
@Data
public class LocalReaderScanVO {

    /** 解析后的绝对路径（规范化后）。 */
    private String absolutePath;

    /** 根目录名（用于前端顶栏展示）。 */
    private String rootName;

    /** Markdown 文档总数。 */
    private int docCount;

    /** 目录树根节点列表。 */
    private List<TreeNode> tree = new ArrayList<>();

    /** 扁平文档列表（按路径排序，便于上一篇/下一篇切换）。 */
    private List<FlatDoc> docs = new ArrayList<>();

    @Data
    public static class TreeNode {
        /** 节点名称（文件名或目录名）。 */
        private String name;
        /** 相对根目录的路径（如 Notes/AI/ML.md）。 */
        private String path;
        /** 节点类型：dir / doc。 */
        private String type;
        /** 子节点（仅 dir 有）。 */
        private List<TreeNode> children = new ArrayList<>();
    }

    @Data
    public static class FlatDoc {
        /** 相对根目录的路径。 */
        private String path;
        /** 文件名。 */
        private String name;
    }
}
