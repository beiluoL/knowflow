package com.knowflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第一性原理单个环节（Architecture-4.1-§15）。
 * <p>
 * 环节定义是服务端常量（rubric 唯一真相源），前端只做渲染，不自行定义顺序与文案口径。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirstPrinciplesStepVO {

    /** 环节 key：facts / assumptions / constraints / mechanism / causality / derivation */
    private String key;

    /** 序号 1-6 */
    private int index;

    /** 环节名称 */
    private String title;

    /** 填写提示 */
    private String hint;

    /** 输入框占位文案 */
    private String placeholder;

    /** 最短有效字数（去空白后） */
    private int minLength;
}
