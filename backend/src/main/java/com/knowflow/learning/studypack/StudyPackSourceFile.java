package com.knowflow.learning.studypack;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/** 将不可变上传字节提供给现有文本提取器。 */
record StudyPackSourceFile(String originalFilename, byte[] bytes) implements MultipartFile {
    public String getName() {
        return "file";
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")
                ? "application/pdf"
                : "text/plain";
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    public long getSize() {
        return bytes.length;
    }

    public byte[] getBytes() {
        return bytes.clone();
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), bytes);
    }
}
