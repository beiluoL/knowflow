package com.knowflow.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传工具：统一保存上传文件，规避 Spring {@code MultipartFile.transferTo()} 在
 * Tomcat 下的相对路径解析 bug（Tomcat 的 Part.write 会把相对目标路径解析到自身的工作目录，
 * 导致 createDirectories 在 JVM 工作目录创建、transferTo 却写到 Tomcat 工作目录，
 * 最终抛出 FileNotFoundException）。这里改用 Files.copy(InputStream, path) 直接落到我们控制的绝对路径。
 */
public final class UploadHelper {

    private UploadHelper() {
    }

    /**
     * 保存上传文件，返回包含 fileName / fileUrl / fileSize / fileType 的结果 Map。
     *
     * @param file      上传的文件
     * @param uploadDir 上传根目录（可为相对或绝对，内部统一转绝对）
     * @return 结果 Map
     * @throws IOException 写入失败时抛出
     */
    public static Map<String, Object> save(MultipartFile file, String uploadDir) throws IOException {
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path uploadPath = baseDir.resolve(dateDir);
        Files.createDirectories(uploadPath);

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // 防御：异常超长扩展名（如 .a.b.c....）直接丢弃，避免污染文件系统
        if (extension.length() > 20) {
            extension = "";
        }
        String newFilename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(newFilename);

        try (var in = file.getInputStream()) {
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileName", originalFilename);
        result.put("fileUrl", "/uploads/" + dateDir + "/" + newFilename);
        result.put("fileSize", file.getSize());
        result.put("fileType", file.getContentType());
        return result;
    }
}
