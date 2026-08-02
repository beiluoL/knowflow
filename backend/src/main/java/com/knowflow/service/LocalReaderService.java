package com.knowflow.service;

import com.knowflow.dto.LocalReaderResolveDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.vo.LocalReaderScanVO;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地阅读器服务：通过后端代理读取本地文件系统，支持按路径加载目录与文件。
 * <p>
 * 浏览器沙箱限制下无法通过路径字符串直接读取本地文件，故由后端统一读取。
 * <ul>
 *   <li>路径校验：有效性、可读性、是否为目录/文件</li>
 *   <li>目录扫描：递归遍历目录树，返回 Markdown 文档、代码文件与子目录</li>
 *   <li>文件读取：读取文本内容（Markdown 渲染 / 代码文件语法高亮由前端处理）</li>
 *   <li>图片读取：返回图片字节数组（Controller 层包装为响应流）</li>
 * </ul>
 */
@Service
public class LocalReaderService {

    /**
     * 支持的文档扩展名集合（Markdown + 纯文本）。
     * 新增文档类型时直接在此集合中追加，无需改动其他逻辑。
     */
    private static final Set<String> DOC_EXTS = new HashSet<>(Arrays.asList(
            "md", "markdown", "txt"
    ));

    /**
     * 支持的代码文件扩展名集合。
     * <p>
     * 前端依据扩展名映射到 highlight.js 语言标识符进行语法高亮。
     * 新增代码类型时直接在此集合中追加，并在前端 EXT_LANG_MAP 同步添加映射即可。
     */
    private static final Set<String> CODE_EXTS = new HashSet<>(Arrays.asList(
            "java", "py", "css", "vue", "js", "ts", "html", "xml", "yml", "yaml",
            "json", "sql", "sh", "bash", "go", "rs", "c", "cpp", "h", "hpp",
            "kt", "swift", "rb", "php", "scss", "less", "toml", "ini", "conf",
            "jsx", "tsx", "dart"
    ));

    /** 文档 + 代码 扩展名并集（用于目录扫描与文件读取校验） */
    private static final Set<String> ALL_TEXT_EXTS;
    static {
        ALL_TEXT_EXTS = new HashSet<>(DOC_EXTS);
        ALL_TEXT_EXTS.addAll(CODE_EXTS);
    }

    /** 支持的图片扩展名 */
    private static final Set<String> IMAGE_EXTS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico"));
    /** 目录递归最大深度（防止过深遍历） */
    private static final int MAX_DEPTH = 10;

    /**
     * 解析路径：将用户输入的路径（绝对或相对）规范化为绝对路径，并校验有效性。
     *
     * @param dto 路径解析请求
     * @return 解析后的绝对路径
     * @throws BusinessException 路径无效、不存在、不可读时抛出
     */
    public String resolvePath(LocalReaderResolveDTO dto) {
        if (dto == null || dto.getPath() == null || dto.getPath().trim().isEmpty()) {
            throw new BusinessException("路径不能为空");
        }
        String rawPath = dto.getPath().trim();
        Path resolved;
        try {
            if (rawPath.startsWith("/") || rawPath.matches("^[A-Za-z]:[\\\\/].*")) {
                // 绝对路径（Unix 或 Windows）
                resolved = Paths.get(rawPath).normalize();
            } else if (dto.getRelativeTo() != null && !dto.getRelativeTo().trim().isEmpty()) {
                // 相对路径，基于上一次的根目录解析
                Path base = Paths.get(dto.getRelativeTo().trim()).normalize();
                resolved = base.resolve(rawPath).normalize();
            } else {
                // 相对路径，无基准目录时基于 JVM 工作目录
                resolved = Paths.get(rawPath).toAbsolutePath().normalize();
            }
        } catch (Exception e) {
            throw new BusinessException("路径格式无效：" + rawPath);
        }

        File file = resolved.toFile();
        if (!file.exists()) {
            throw new BusinessException("路径不存在：" + resolved);
        }
        if (!file.canRead()) {
            throw new BusinessException("路径不可读（权限不足）：" + resolved);
        }
        return resolved.toString();
    }

    /**
     * 扫描目录：递归构建目录树与扁平文档列表。
     *
     * @param absolutePath 已校验的目录绝对路径
     * @return 目录扫描结果
     */
    public LocalReaderScanVO scanDirectory(String absolutePath) {
        File root = new File(absolutePath);
        if (!root.exists()) {
            throw new BusinessException("目录不存在：" + absolutePath);
        }
        if (!root.isDirectory()) {
            throw new BusinessException("路径不是目录：" + absolutePath);
        }
        if (!root.canRead()) {
            throw new BusinessException("目录不可读（权限不足）：" + absolutePath);
        }

        LocalReaderScanVO vo = new LocalReaderScanVO();
        vo.setAbsolutePath(absolutePath);
        vo.setRootName(root.getName());

        // 构建目录树
        List<LocalReaderScanVO.TreeNode> tree = new ArrayList<>();
        List<LocalReaderScanVO.FlatDoc> flatDocs = new ArrayList<>();
        buildTree(root, root, tree, flatDocs, 0);

        // 扁平列表按路径排序
        flatDocs.sort(Comparator.comparing(LocalReaderScanVO.FlatDoc::getPath));
        vo.setDocs(flatDocs);
        vo.setDocCount(flatDocs.size());
        return vo;
    }

    /**
     * 递归构建目录树。
     */
    private void buildTree(File rootDir, File currentDir, List<LocalReaderScanVO.TreeNode> nodes,
                           List<LocalReaderScanVO.FlatDoc> flatDocs, int depth) {
        if (depth > MAX_DEPTH) return;

        File[] children = currentDir.listFiles();
        if (children == null) return;

        // 先排序：目录在前，文件在后，各自按名称排序
        List<File> sorted = new ArrayList<>(Arrays.asList(children));
        sorted.sort((a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File child : sorted) {
            // 跳过隐藏文件与常见无关目录
            if (child.isHidden()) continue;
            if (child.isDirectory() && isIgnoredDir(child.getName())) continue;

            String relativePath = rootDir.toPath().relativize(child.toPath()).toString()
                    .replace(File.separatorChar, '/');

            if (child.isDirectory()) {
                LocalReaderScanVO.TreeNode dirNode = new LocalReaderScanVO.TreeNode();
                dirNode.setName(child.getName());
                dirNode.setPath(relativePath);
                dirNode.setType("dir");
                List<LocalReaderScanVO.TreeNode> dirChildren = new ArrayList<>();
                buildTree(rootDir, child, dirChildren, flatDocs, depth + 1);
                dirNode.setChildren(dirChildren);
                // 仅添加非空目录（避免展示空文件夹）
                if (!dirChildren.isEmpty()) {
                    nodes.add(dirNode);
                }
            } else if (child.isFile()) {
                String ext = getExtension(child.getName());
                if (!ALL_TEXT_EXTS.contains(ext)) continue;
                LocalReaderScanVO.TreeNode docNode = new LocalReaderScanVO.TreeNode();
                docNode.setName(child.getName());
                docNode.setPath(relativePath);
                docNode.setType("doc");
                nodes.add(docNode);

                LocalReaderScanVO.FlatDoc flat = new LocalReaderScanVO.FlatDoc();
                flat.setPath(relativePath);
                flat.setName(child.getName());
                flatDocs.add(flat);
            }
        }
    }

    /**
     * 判断是否为忽略的目录（.git, node_modules, .obsidian 配置目录保留但隐藏文件已跳过）。
     */
    private boolean isIgnoredDir(String name) {
        return ".git".equals(name) || "node_modules".equals(name)
                || ".trash".equals(name) || "__pycache__".equals(name);
    }

    /**
     * 读取文本文件内容（Markdown / 代码文件 / 纯文本）。
     * <p>
     * 扩展名校验使用 {@link #ALL_TEXT_EXTS}，覆盖文档与代码文件两类。
     * 文本内容统一以 UTF-8 读取，前端根据扩展名决定渲染方式。
     *
     * @param rootAbsolutePath 根目录绝对路径
     * @param relativePath      文档相对路径
     * @return 文件文本内容
     */
    public String readDocContent(String rootAbsolutePath, String relativePath) {
        File file = resolveFile(rootAbsolutePath, relativePath);
        if (!file.isFile()) {
            throw new BusinessException("不是文件：" + relativePath);
        }
        String ext = getExtension(file.getName());
        if (!ALL_TEXT_EXTS.contains(ext)) {
            throw new BusinessException("不支持的文件类型：" + ext);
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取文件失败：" + e.getMessage());
        }
    }

    /**
     * 读取图片文件字节数组。
     *
     * @param rootAbsolutePath 根目录绝对路径
     * @param relativePath      图片相对路径
     * @return 图片字节数组
     */
    public byte[] readImage(String rootAbsolutePath, String relativePath) {
        File file = resolveFile(rootAbsolutePath, relativePath);
        if (!file.isFile()) {
            throw new BusinessException("图片不存在：" + relativePath);
        }
        String ext = getExtension(file.getName());
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BusinessException("不支持的图片类型：" + ext);
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new BusinessException("读取图片失败：" + e.getMessage());
        }
    }

    /**
     * 根据相对路径解析文件，包含路径穿越防护。
     */
    private File resolveFile(String rootAbsolutePath, String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new BusinessException("文件路径不能为空");
        }
        Path root = Paths.get(rootAbsolutePath).normalize();
        Path resolved = root.resolve(relativePath).normalize();

        // 路径穿越防护：解析后的路径必须在根目录下
        if (!resolved.startsWith(root)) {
            throw new BusinessException("非法路径（超出根目录范围）");
        }
        File file = resolved.toFile();
        if (!file.exists()) {
            throw new BusinessException("文件不存在：" + relativePath);
        }
        if (!file.canRead()) {
            throw new BusinessException("文件不可读（权限不足）：" + relativePath);
        }
        return file;
    }

    /**
     * 查找图片文件：根据文档相对路径推断图片在仓库中的实际位置。
     * <p>
     * 查找策略（按优先级）：
     * 1. 精确路径（相对于根目录）
     * 2. 文档同级目录
     * 3. image/images/attachments/assets 等常见图片目录
     *
     * @param rootAbsolutePath 根目录绝对路径
     * @param imagePath        图片路径（可能是文件名或相对路径）
     * @param docRelativePath  引用该图片的文档相对路径（用于推断同级目录）
     * @return 图片字节数组；未找到返回 null
     */
    public byte[] findImage(String rootAbsolutePath, String imagePath, String docRelativePath) {
        Path root = Paths.get(rootAbsolutePath).normalize();
        String imageName = getFileName(imagePath);

        // 候选路径列表
        List<String> candidates = new ArrayList<>();
        // 1. 精确相对路径
        candidates.add(imagePath);
        // 2. 文档同级目录
        String docDir = getDirPath(docRelativePath);
        if (docDir != null && !docDir.isEmpty()) {
            candidates.add(docDir + "/" + imageName);
        }
        // 3. 常见图片目录
        for (String imgDir : new String[]{"image", "images", "attachments", "assets"}) {
            candidates.add(imgDir + "/" + imageName);
            candidates.add(imgDir + "/" + imagePath);
        }

        for (String candidate : candidates) {
            try {
                Path resolved = root.resolve(candidate).normalize();
                if (!resolved.startsWith(root)) continue;
                File file = resolved.toFile();
                if (file.isFile() && file.canRead()) {
                    String ext = getExtension(file.getName());
                    if (IMAGE_EXTS.contains(ext)) {
                        return Files.readAllBytes(file.toPath());
                    }
                }
            } catch (IOException ignored) {
                // 继续尝试下一个候选
            }
        }
        return null;
    }

    /**
     * 获取文件扩展名（小写，不含点）。
     */
    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    /**
     * 获取文件名（含扩展名）。
     */
    private String getFileName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /**
     * 获取目录路径（不含文件名）。
     */
    private String getDirPath(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash > 0 ? relativePath.substring(0, slash) : "";
    }
}
