package com.knowflow.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.context.annotation.EnableMBeanExport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传文件统一配置（Single Source of Truth）。
 *
 * 替代原先分散在 WebMvcConfig / PrivateMessageController / StudyGroupController /
 * DocServiceImpl 四处的 {@code @Value("${app.upload.dir:${user.home}/knowflow/uploads}")} 注入，
 * 集中管理上传根目录，并支持运行时通过 FileController 动态调整。
 *
 * 注意：动态修改 uploadDir 仅影响后续上传与新增的文件浏览，不会迁移已有文件；
 * WebMvcConfig 的静态资源映射在 Bean 初始化时建立，运行时修改目录后需重启才能生效映射。
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.upload")
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class UploadConfigProperties {

    /**
     * 上传根目录。默认 ${user.home}/knowflow/uploads。
     * 可通过 application.yml / application-local.yml 的 app.upload.dir 覆盖。
     */
    private String dir = System.getProperty("user.home") + "/knowflow/uploads";

    /**
     * 启动时确保上传目录存在。
     */
    @PostConstruct
    public void init() throws IOException {
        Path path = Paths.get(dir).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        log.info("Upload directory initialized at: {}", path);
    }

    /**
     * 返回上传目录的绝对路径（带尾部斜杠），便于拼接子路径。
     */
    public String getAbsoluteDir() {
        String abs = Paths.get(dir).toAbsolutePath().normalize().toString();
        if (!abs.endsWith("/") && !abs.endsWith("\\")) {
            abs = abs + "/";
        }
        return abs;
    }
}
