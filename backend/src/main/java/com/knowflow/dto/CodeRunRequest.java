package com.knowflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码在线运行请求。
 * 支持语言：python / java / javascript / cpp
 */
@Data
@NoArgsConstructor
public class CodeRunRequest {
    /** 语言标识：python / java / javascript / cpp（大小写与别名自动归一） */
    private String language;

    /** 用户提交的源代码内容 */
    private String code;

    /** 可选标准输入（运行期写入子进程 stdin） */
    private String stdin;

    /** 可选超时覆盖（毫秒）；不传使用服务端默认值 */
    private Long timeLimitMs;

    /**
     * 是否工作区模式（SC1-IDE-02 可重置实验沙箱）。
     * true 时不再写入临时目录，而是运行用户持久化工作区中由 {@link #entryFile} 指定的入口文件。
     */
    private Boolean workspace;

    /** 工作区模式下的入口文件名（如 main.py / Main.java），相对于用户工作区根目录 */
    private String entryFile;
}
