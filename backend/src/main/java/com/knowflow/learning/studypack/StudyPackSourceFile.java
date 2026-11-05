package com.knowflow.learning.studypack;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/** Immutable uploaded bytes exposed to the existing text extractor. */
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
