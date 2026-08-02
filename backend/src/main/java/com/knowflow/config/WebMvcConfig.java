package com.knowflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 - 静态资源映射
 *
 * 将 /uploads/** 映射到上传目录，使上传的文件可通过 HTTP 直接访问。
 * 上传目录由 {@link UploadConfigProperties} 统一管理。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadConfigProperties uploadConfig;

    public WebMvcConfig(UploadConfigProperties uploadConfig) {
        this.uploadConfig = uploadConfig;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射上传文件目录为静态资源，使用绝对路径避免相对路径解析问题
        String absolutePath = uploadConfig.getAbsoluteDir();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath);
    }
}
