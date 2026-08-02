package com.knowflow.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 基于本地文件的 {@link MultipartFile} 实现。
 * <p>
 * 用于「路径导入」场景：将本地文件系统中的文件包装为 MultipartFile，
 * 复用 {@code KnowledgeImportService} 现有的批量导入流程
 * （进度回调、增量去重、图片迁移、标签生成等）。
 * <p>
 * 与上传文件不同，本实现直接读取本地文件字节，无需经过 HTTP multipart 解析。
 * 文件名使用相对路径（如 {@code Notes/AI/ML.md}），以便后端还原目录结构。
 *
 * @param file         本地文件
 * @param relativePath 相对根目录的路径（作为 originalFilename）
 */
public record LocalFileMultipartFile(File file, String relativePath) implements MultipartFile {

    public LocalFileMultipartFile(Path filePath, String relativePath) {
        this(filePath.toFile(), relativePath);
    }

    @Override
    public String getName() {
        // multipart 字段名，路径导入场景固定为 "files"
        return "files";
    }

    @Override
    public String getOriginalFilename() {
        // 使用相对路径作为文件名，后端据此还原目录结构
        return relativePath;
    }

    @Override
    public String getContentType() {
        try {
            return Files.probeContentType(file.toPath());
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    @Override
    public boolean isEmpty() {
        return file.length() == 0;
    }

    @Override
    public long getSize() {
        return file.length();
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        Files.copy(file.toPath(), dest.toPath());
    }
}
