package com.zhishiku.dto;

import com.zhishiku.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DocQueryDTO extends PageQuery {

    private String keyword;

    private Long categoryId;

    private Integer difficulty;

    private Integer status;
}
