package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.config.UploadConfigProperties;
import com.knowflow.exception.BusinessException;
import com.knowflow.vo.UploadFileVO;
import com.knowflow.vo.UploadStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件管理接口（管理员）。
 *
 * 提供上传文件列表查询、删除、存储统计、上传目录配置查询/修改能力。
 * 所有接口前缀 /api/admin/files，需 ADMIN 角色。
 *
 * 注意：
 *  - 修改上传目录仅更新内存中的 UploadConfigProperties 与 application-local.yml，
 *    已有文件不会迁移；WebMvcConfig 的静态资源映射在 Bean 初始化时建立，
 *    修改目录后需重启后端才能让 /uploads/** 映射到新目录。
 *  - 因此前端在修改目录后会提示用户重启后端。
 */
@Slf4j
@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
public class FileController {

    private final UploadConfigProperties uploadConfig;

    /** 图片扩展名集合 */
    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico"
    );

    @Operation(summary = "上传文件列表（分页 + 类型筛选）")
    @GetMapping
    public Result<Map<String, Object>> listFiles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {

        Path baseDir = Paths.get(uploadConfig.getDir()).toAbsolutePath().normalize();
        if (!Files.exists(baseDir)) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("list", Collections.emptyList());
            empty.put("total", 0);
            return Result.success(empty);
        }

        List<UploadFileVO> allFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(baseDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        UploadFileVO vo = toVO(baseDir, path);
                        if (vo == null) return;
                        // 类型筛选
                        if (type != null && !type.isEmpty()) {
                            if ("image".equals(type) && !Boolean.TRUE.equals(vo.getIsImage())) return;
                            if ("other".equals(type) && Boolean.TRUE.equals(vo.getIsImage())) return;
                        }
                        // 关键词筛选（文件名模糊匹配）
                        if (keyword != null && !keyword.isEmpty()) {
                            if (!vo.getFileName().toLowerCase().contains(keyword.toLowerCase())) return;
                        }
                        allFiles.add(vo);
                    });
        } catch (IOException e) {
            log.error("遍历上传目录失败: {}", baseDir, e);
            throw new BusinessException(500, "读取文件列表失败: " + e.getMessage());
        }

        // 按修改时间倒序
        allFiles.sort(Comparator.comparing(UploadFileVO::getLastModified, Comparator.nullsLast(Comparator.reverseOrder())));

        // 分页
        int total = allFiles.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<UploadFileVO> pageList = allFiles.subList(from, to);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        return Result.success(result);
    }

    @Operation(summary = "存储统计")
    @GetMapping("/stats")
    public Result<UploadStatsVO> getStats() {
        Path baseDir = Paths.get(uploadConfig.getDir()).toAbsolutePath().normalize();
        UploadStatsVO vo = new UploadStatsVO();
        vo.setUploadDir(baseDir.toString());

        if (!Files.exists(baseDir)) {
            vo.setTotalFiles(0L);
            vo.setTotalSize(0L);
            vo.setTotalSizeReadable("0 B");
            vo.setImageCount(0L);
            vo.setOtherCount(0L);
            return Result.success(vo);
        }

        AtomicLong totalSize = new AtomicLong(0);
        AtomicLong totalFiles = new AtomicLong(0);
        AtomicLong imageCount = new AtomicLong(0);

        try (Stream<Path> stream = Files.walk(baseDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    long size = Files.size(path);
                    totalSize.addAndGet(size);
                    totalFiles.incrementAndGet();
                    String ext = getExtension(path.getFileName().toString());
                    if (IMAGE_EXTS.contains(ext)) {
                        imageCount.incrementAndGet();
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            log.error("统计上传目录失败: {}", baseDir, e);
        }

        vo.setTotalFiles(totalFiles.get());
        vo.setTotalSize(totalSize.get());
        vo.setTotalSizeReadable(humanReadableSize(totalSize.get()));
        vo.setImageCount(imageCount.get());
        vo.setOtherCount(totalFiles.get() - imageCount.get());
        return Result.success(vo);
    }

    @Operation(summary = "删除文件")
    @DeleteMapping
    public Result<Void> deleteFile(@RequestParam String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new BusinessException(400, "fileUrl 不能为空");
        }
        // 安全校验：仅允许删除 /uploads/ 下的文件
        if (!fileUrl.startsWith("/uploads/")) {
            throw new BusinessException(400, "仅允许删除上传目录下的文件");
        }
        String relativePath = fileUrl.substring("/uploads/".length());
        Path baseDir = Paths.get(uploadConfig.getDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(relativePath).normalize();

        // 防止路径穿越攻击
        if (!target.startsWith(baseDir)) {
            throw new BusinessException(400, "非法路径");
        }
        if (!Files.exists(target)) {
            throw new BusinessException(404, "文件不存在");
        }
        try {
            Files.delete(target);
            log.info("删除上传文件: {}", target);
        } catch (IOException e) {
            throw new BusinessException(500, "删除失败: " + e.getMessage());
        }
        return Result.success();
    }

    @Operation(summary = "查询上传目录配置")
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("uploadDir", uploadConfig.getDir());
        config.put("absoluteDir", uploadConfig.getAbsoluteDir());
        config.put("exists", Files.exists(Paths.get(uploadConfig.getDir())));
        return Result.success(config);
    }

    @Operation(summary = "修改上传目录配置（需重启后端生效）")
    @PutMapping("/config")
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, String> body) {
        String newDir = body.get("uploadDir");
        if (newDir == null || newDir.trim().isEmpty()) {
            throw new BusinessException(400, "uploadDir 不能为空");
        }
        // 安全校验：禁止设置为系统敏感目录
        String normalized = newDir.trim();
        Path newPath = Paths.get(normalized).toAbsolutePath().normalize();
        // 简单防护：禁止根目录、禁止 /etc /usr /var 等系统目录
        if (newPath.toString().equals("/") || newPath.toString().equals("/System") ||
                newPath.toString().startsWith("/etc") || newPath.toString().startsWith("/usr") ||
                newPath.toString().startsWith("/var") || newPath.toString().startsWith("/bin") ||
                newPath.toString().startsWith("/sbin") || newPath.toString().startsWith("/Windows")) {
            throw new BusinessException(400, "禁止设置为系统敏感目录");
        }

        // 尝试创建目录（验证可写）
        try {
            Files.createDirectories(newPath);
        } catch (IOException e) {
            throw new BusinessException(400, "目录创建失败，请检查路径权限: " + e.getMessage());
        }

        // 更新内存配置
        uploadConfig.setDir(normalized);

        // 持久化到 application-local.yml（下次启动生效）
        try {
            persistConfigToLocalYml(normalized);
        } catch (IOException e) {
            log.warn("持久化上传目录配置到 application-local.yml 失败: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("uploadDir", normalized);
        result.put("absoluteDir", uploadConfig.getAbsoluteDir());
        result.put("message", "目录配置已更新，需重启后端才能让 /uploads 静态资源映射生效");
        return Result.success(result);
    }

    /**
     * 将上传目录配置持久化到 application-local.yml。
     * 简单实现：追加 app.upload.dir 配置到文件末尾。
     */
    private void persistConfigToLocalYml(String dir) throws IOException {
        Path ymlPath = Paths.get("src/main/resources/application-local.yml");
        // 若文件不存在，创建
        if (!Files.exists(ymlPath)) {
            Files.writeString(ymlPath, "# 本地配置（不提交到版本库）\n\napp:\n  upload:\n    dir: " + dir + "\n",
                    StandardOpenOption.CREATE);
            return;
        }
        // 读取现有内容
        String content = Files.readString(ymlPath);
        // 若已有 app.upload.dir 配置，替换；否则追加
        if (content.contains("app:") && content.contains("upload:")) {
            content = content.replaceAll("dir:\\s*[^\\n]+", "dir: " + dir);
            Files.writeString(ymlPath, content, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            String append = "\napp:\n  upload:\n    dir: " + dir + "\n";
            Files.writeString(ymlPath, content + append, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    /** Path → UploadFileVO 转换 */
    private UploadFileVO toVO(Path baseDir, Path path) {
        try {
            UploadFileVO vo = new UploadFileVO();
            String fileName = path.getFileName().toString();
            vo.setFileName(fileName);
            String relativePath = baseDir.relativize(path).toString().replace("\\", "/");
            vo.setRelativePath(relativePath);
            vo.setFileUrl("/uploads/" + relativePath);
            vo.setFileSize(Files.size(path));
            vo.setExtension(getExtension(fileName));
            vo.setLastModified(Files.getLastModifiedTime(path).toMillis());
            vo.setIsImage(IMAGE_EXTS.contains(vo.getExtension().toLowerCase()));
            return vo;
        } catch (IOException e) {
            return null;
        }
    }

    /** 获取文件扩展名（小写，不含点） */
    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) return "";
        return fileName.substring(idx + 1).toLowerCase();
    }

    /** 字节大小 → 人类可读 */
    private String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
