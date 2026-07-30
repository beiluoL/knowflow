package com.knowflow.service.impl;

import com.knowflow.dto.CodeWorkspaceFileVO;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.CodeWorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 代码工作区服务实现（SC1-IDE-02）。
 * <p>目录结构：{@code workspaceRoot/{userId}/}，文件单层存放。
 * 所有 path 均经过 {@link #safeResolve} 校验，禁止绝对路径与 {@code ..} 穿越，杜绝越权访问。
 */
@Slf4j
@Service
public class CodeWorkspaceServiceImpl implements CodeWorkspaceService {

    /** 工作区根目录（相对或绝对），默认 ./code-workspaces */
    @Value("${code.execution.workspace-root:./code-workspaces}")
    private String workspaceRoot;

    private static final List<String> ALLOWED_EXT = List.of(
            "py", "js", "ts", "java", "cpp", "cc", "cxx", "c", "h", "go", "rs", "sql", "txt", "md", "json");

    @Override
    public Path getWorkspaceDir(Long userId) {
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        Path dir = root.resolve(String.valueOf(userId)).normalize();
        // 二次校验：确保解析结果仍在 root 之内（userId 不可能含 ..，此处仅防御）
        if (!dir.startsWith(root)) {
            throw new BusinessException("非法的工作区路径");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("创建工作区目录失败：" + e.getMessage());
        }
        return dir;
    }

    @Override
    public List<CodeWorkspaceFileVO> listFiles(Long userId) {
        Path dir = getWorkspaceDir(userId);
        List<CodeWorkspaceFileVO> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> result.add(toVO(p)));
        } catch (IOException e) {
            throw new BusinessException("读取工作区文件列表失败：" + e.getMessage());
        }
        return result;
    }

    @Override
    public CodeWorkspaceFileVO saveFile(Long userId, String path, String content) {
        Path dir = getWorkspaceDir(userId);
        Path target = safeResolve(dir, path);
        try {
            // 允许新建时创建父目录（本实现为单层，父目录即工作区根）
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
            return toVO(target);
        } catch (IOException e) {
            throw new BusinessException("写入工作区文件失败：" + e.getMessage());
        }
    }

    @Override
    public void deleteFile(Long userId, String path) {
        Path dir = getWorkspaceDir(userId);
        Path target = safeResolve(dir, path);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BusinessException("删除工作区文件失败：" + e.getMessage());
        }
    }

    @Override
    public void reset(Long userId) {
        Path dir = getWorkspaceDir(userId);
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 单个失败不阻断其余
                }
            });
        } catch (IOException e) {
            throw new BusinessException("重置工作区失败：" + e.getMessage());
        }
    }

    /** 将工作区内相对路径解析为绝对路径，并校验不越出工作区目录 */
    private Path safeResolve(Path dir, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new BusinessException("文件路径不能为空");
        }
        if (rawPath.startsWith("/") || rawPath.startsWith("\\") || rawPath.contains("..")) {
            throw new BusinessException("非法的文件路径：" + rawPath);
        }
        Path resolved = dir.resolve(rawPath).normalize();
        if (!resolved.startsWith(dir)) {
            throw new BusinessException("非法的文件路径：" + rawPath);
        }
        String name = resolved.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型：" + ext + "（允许 " + String.join("/", ALLOWED_EXT) + "）");
        }
        return resolved;
    }

    private CodeWorkspaceFileVO toVO(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
        String language = switch (ext) {
            case "py" -> "python";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "java" -> "java";
            case "cpp", "cc", "cxx", "c" -> "cpp";
            case "go" -> "go";
            case "rs" -> "rust";
            case "sql" -> "sql";
            default -> "text";
        };
        String content;
        try {
            content = Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            content = "";
        }
        long size;
        try {
            size = Files.size(p);
        } catch (IOException e) {
            size = 0;
        }
        return CodeWorkspaceFileVO.builder()
                .path(name)
                .name(name)
                .size(size)
                .language(language)
                .content(content)
                .build();
    }
}
