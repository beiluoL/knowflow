package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 第一性原理 rubric（六环节定义 + 计分口径，Architecture-4.1-§15/§16）。
 * <p>
 * 全部为服务端常量，前端不重复定义（避免口径漂移）。
 */
@Data
public class FirstPrinciplesRubricVO {

    /** 六环节定义（顺序即展示顺序） */
    private List<FirstPrinciplesStepVO> steps;

    /** 评分口径说明（如实披露：只评结构，不评对错） */
    private String scoringNote;
}
