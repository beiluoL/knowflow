package com.knowflow.service;

import com.knowflow.dto.LocalReaderResolveDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.util.LocalFileMultipartFile;
import com.knowflow.vo.PathImportScanVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路径导入服务：根据用户输入的本地路径（绝对/相对）收集待导入文件。
 * <p>
 * 与 {@link LocalReaderService} 的区别：
 * <ul>
 *     <li>LocalReaderService：面向「本地阅读」场景，仅扫描 Markdown + 代码文件，返回文本内容</li>
 *     <li>PathImportService：面向「路径导入」场景，扫描所有导入支持的文件（含富文档、图片），
 *         返回 {@link MultipartFile} 数组，复用 {@code KnowledgeImportService} 现有导入流程</li>
 * </ul>
 * <p>
 * 支持两种模式：
 * <ol>
 *     <li>目录模式：扫描目录下所有支持文件（文档 + 图片），保留相对路径</li>
 *     <li>单文件模式：直接导入指定文件（路径指向单个文件而非目录）</li>
 * </ol>
 */
@Service
public class PathImportService {

    private final LocalReaderService localReaderService;

    public PathImportService(LocalReaderService localReaderService) {
        this.localReaderService = localReaderService;
    }

    /**
     * 导入支持的文档扩展名（与 KnowledgeImportServiceImpl.DOC_EXTS 对齐）。
     * 含 Markdown / 富文档 / 代码文件。
     */
    private static final Set<String> DOC_EXTS = new HashSet<>(Arrays.asList(
            "md", "markdown", "txt",
            "pdf", "doc", "docx", "ppt", "pptx", "rtf", "html", "htm",
            // 代码文件
            "java", "py", "css", "vue", "js", "ts", "xml", "yml", "yaml",
            "json", "sql", "sh", "bash", "go", "rs", "c", "cpp", "h", "hpp",
            "kt", "swift", "rb", "php", "scss", "less", "toml", "ini", "conf",
            "jsx", "tsx", "dart"
    ));

    /** 图片扩展名（与 KnowledgeImportServiceImpl.IMAGE_EXTS 对齐） */
    private static final Set<String> IMAGE_EXTS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico"
    ));

    /** 文档 + 图片并集 */
    private static final Set<String> ALL_EXTS;
    static {
        ALL_EXTS = new HashSet<>(DOC_EXTS);
        ALL_EXTS.addAll(IMAGE_EXTS);
    }

    /** 目录递归最大深度（防止过深遍历） */
    private static final int MAX_DEPTH = 10;

    /**
     * 解析路径：委托给 {@link LocalReaderService#resolvePath}，
     * 保持路径校验逻辑（绝对/相对、存在性、可读性）一致。
     *
     * @param path       用户输入的路径
     * @param relativeTo 相对基准（可选，用于相对路径解析）
     * @return 解析后的绝对路径
     */
    public String resolvePath(String path, String relativeTo) {
        LocalReaderResolveDTO dto = new LocalReaderResolveDTO();
        dto.setPath(path);
        dto.setRelativeTo(relativeTo);
        return localReaderService.resolvePath(dto);
    }

    /**
     * 扫描路径：返回待导入的文件列表，供前端预览确认。
     * <p>
     * 自动判断路径类型：
     * <ul>
     *     <li>目录：递归扫描所有支持文件</li>
     *     <li>单文件：返回该文件本身（如为支持的类型）</li>
     * </ul>
     *
     * @param absolutePath 已校验的绝对路径
     * @return 扫描结果（含文档数、图片数、目录数、文件列表）
     */
    public PathImportScanVO scanForImport(String absolutePath) {
        File root = new File(absolutePath);
        if (!root.exists()) {
            throw new BusinessException("路径不存在：" + absolutePath);
        }
        if (!root.canRead()) {
            throw new BusinessException("路径不可读（权限不足）：" + absolutePath);
        }

        PathImportScanVO vo = new PathImportScanVO();
        vo.setAbsolutePath(absolutePath);
        vo.setRootName(root.getName());
        vo.setFile(root.isFile());

        List<PathImportScanVO.FileEntry> entries = new ArrayList<>();
        if (root.isFile()) {
            // 单文件模式
            String ext = getExtension(root.getName());
            if (!ALL_EXTS.contains(ext)) {
                throw new BusinessException("不支持的文件类型：" + ext + "（支持 Markdown/代码/富文档/图片）");
            }
            PathImportScanVO.FileEntry entry = new PathImportScanVO.FileEntry();
            entry.setName(root.getName());
            entry.setPath(root.getName()); // 单文件模式下相对路径即文件名
            entry.setType(DOC_EXTS.contains(ext) ? "doc" : "image");
            entry.setExt(ext);
            entry.setSize(root.length());
            entries.add(entry);

            vo.setDocCount(DOC_EXTS.contains(ext) ? 1 : 0);
            vo.setImageCount(IMAGE_EXTS.contains(ext) ? 1 : 0);
            vo.setDirCount(0);
        } else {
            // 目录模式：递归扫描
            Set<String> dirSet = new HashSet<>();
            collectFiles(root, root, entries, dirSet, 0);
            entries.sort(Comparator.comparing(PathImportScanVO.FileEntry::getPath));

            int docCount = 0, imgCount = 0;
            for (PathImportScanVO.FileEntry e : entries) {
                if ("doc".equals(e.getType())) docCount++;
                else if ("image".equals(e.getType())) imgCount++;
            }
            vo.setDocCount(docCount);
            vo.setImageCount(imgCount);
            vo.setDirCount(dirSet.size());
        }
        vo.setFiles(entries);
        return vo;
    }

    /**
     * 收集待导入文件为 {@link MultipartFile} 数组。
     * <p>
     * 用于实际导入阶段：将扫描到的文件读取为字节，包装为 {@link LocalFileMultipartFile}，
     * 交由 {@code KnowledgeImportService.importDirectoryWithProgress} 处理。
     *
     * @param absolutePath 已校验的绝对路径
     * @return 待导入文件数组（文档 + 图片）
     * @throws IOException 读取文件失败
     */
    public MultipartFile[] collectFiles(String absolutePath) throws IOException {
        File root = new File(absolutePath);
        if (!root.exists() || !root.canRead()) {
            throw new BusinessException("路径不存在或不可读：" + absolutePath);
        }

        List<MultipartFile> result = new ArrayList<>();
        if (root.isFile()) {
            String ext = getExtension(root.getName());
            if (!ALL_EXTS.contains(ext)) {
                throw new BusinessException("不支持的文件类型：" + ext);
            }
            result.add(new LocalFileMultipartFile(root, root.getName()));
        } else {
            collectFilesRecursive(root, root, result, 0);
        }
        return result.toArray(new MultipartFile[0]);
    }

    /**
     * 递归收集文件（用于实际导入）。
     */
    private void collectFilesRecursive(File rootDir, File currentDir,
                                        List<MultipartFile> result, int depth) throws IOException {
        if (depth > MAX_DEPTH) return;
        File[] children = currentDir.listFiles();
        if (children == null) return;

        List<File> sorted = new ArrayList<>(Arrays.asList(children));
        sorted.sort((a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File child : sorted) {
            if (child.isHidden()) continue;
            if (child.isDirectory() && isIgnoredDir(child.getName())) continue;

            String relativePath = rootDir.toPath().relativize(child.toPath()).toString()
                    .replace(File.separatorChar, '/');

            if (child.isDirectory()) {
                collectFilesRecursive(rootDir, child, result, depth + 1);
            } else if (child.isFile()) {
                String ext = getExtension(child.getName());
                if (!ALL_EXTS.contains(ext)) continue;
                result.add(new LocalFileMultipartFile(child, relativePath));
            }
        }
    }

    /**
     * 递归收集文件元数据（用于扫描预览，不读取文件内容）。
     */
    private void collectFiles(File rootDir, File currentDir,
                              List<PathImportScanVO.FileEntry> entries,
                              Set<String> dirSet, int depth) {
        if (depth > MAX_DEPTH) return;
        File[] children = currentDir.listFiles();
        if (children == null) return;

        List<File> sorted = new ArrayList<>(Arrays.asList(children));
        sorted.sort((a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File child : sorted) {
            if (child.isHidden()) continue;
            if (child.isDirectory() && isIgnoredDir(child.getName())) continue;

            String relativePath = rootDir.toPath().relativize(child.toPath()).toString()
                    .replace(File.separatorChar, '/');

            if (child.isDirectory()) {
                dirSet.add(relativePath);
                collectFiles(rootDir, child, entries, dirSet, depth + 1);
            } else if (child.isFile()) {
                String ext = getExtension(child.getName());
                if (!ALL_EXTS.contains(ext)) continue;
                PathImportScanVO.FileEntry entry = new PathImportScanVO.FileEntry();
                entry.setName(child.getName());
                entry.setPath(relativePath);
                entry.setType(DOC_EXTS.contains(ext) ? "doc" : "image");
                entry.setExt(ext);
                entry.setSize(child.length());
                entries.add(entry);
            }
        }
    }

    /**
     * 判断是否为忽略的目录（与 LocalReaderService 保持一致）。
     */
    private boolean isIgnoredDir(String name) {
        return ".git".equals(name) || "node_modules".equals(name)
                || ".trash".equals(name) || "__pycache__".equals(name)
                || ".idea".equals(name) || "target".equals(name) || "build".equals(name);
    }

    /**
     * 获取文件扩展名（小写，不含点）。
     */
    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}
