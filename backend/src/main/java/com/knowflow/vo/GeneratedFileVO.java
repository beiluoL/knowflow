package com.knowflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 从模型输出中解析出来的单个待落盘代码文件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedFileVO {

    /** 相对文件名，例如 index.html；已由后端做过路径穿越校验。 */
    private String fileName;

    /** 代码语言标识（html/css/js 等），来源于 Markdown 代码块的语言标记。 */
    private String language;

    /** 文件正文内容。 */
    private String content;

    /** 内容字节数，便于前端展示体积。 */
    private Integer size;
}
