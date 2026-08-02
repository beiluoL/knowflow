package com.knowflow.dto;

import lombok.Data;

/**
 * 本地阅读器：路径解析请求 DTO。
 * <p>
 * 用户输入的路径可能是绝对路径或相对路径。
 * 相对路径基于 {@code relativeTo}（上一次加载的根目录绝对路径）解析。
 */
@Data
public class LocalReaderResolveDTO {

    /** 用户输入的原始路径（绝对或相对）。 */
    private String path;

    /** 相对路径基准目录（上一次加载的根目录绝对路径）；为空时基于 JVM 工作目录。 */
    private String relativeTo;
}
