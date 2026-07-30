package com.knowflow.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件型文档上传的元信息（与原始文件分离，通过 multipart 的 meta 部分以 JSON 传递）。
 */
@Data
public class DocUploadMetaDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文档标题；为空时回退为原始文件名。 */
    private String title;

    /** 文档摘要/描述。 */
    private String summary;

    /** 标签，逗号分隔。 */
    private String tags;

    /** 归属分类 ID（逻辑外键 doc_category.id）。 */
    private Long categoryId;

    /** 难度等级，数值越大越难。 */
    private Integer difficulty;

    /** 状态，0 草稿 / 1 已发布。 */
    private Integer status;
}
