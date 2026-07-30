package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 在线练习题目视图对象：面向答题页，包含题干、选项、答案与解析（支持「看答案」模式）。
 */
@Data
public class QuizPracticeVO {

    private Long id;

    private String title;

    private String content;

    /** 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / TRUE_FALSE / SHORT_ANSWER */
    private String questionType;

    /** 选项列表（选择题用，已由 JSON 解析为数组；非选择题为空列表） */
    private List<String> options;

    /** 正确答案：选择题为选项索引（"0" 或 "0,2"），判断题为 true/false，其余为文本 */
    private String answer;

    /** 答案解析 */
    private String explanation;

    /** 难度：1 简单 / 2 中等 / 3 困难 */
    private Integer difficulty;

    /** 标签 */
    private String tags;
}
