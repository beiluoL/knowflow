package com.knowflow.vo;

import lombok.Data;

/**
 * 错题重练 VO：从学习错题本中取未掌握的错题，供用户再次作答。
 */
@Data
public class QuizMistakeVO {

    /** 错题记录ID */
    private Long id;
    /** 原始题目文本 */
    private String question;
    /** 用户上次的作答 */
    private String wrongAnswer;
    /** 正确答案 */
    private String correctAnswer;
    /** 分类 */
    private String category;
    /** 难度 */
    private Integer difficulty;
    /** 已复习次数 */
    private Integer reviewCount;
    /** 是否已掌握 */
    private Boolean mastered;
}
