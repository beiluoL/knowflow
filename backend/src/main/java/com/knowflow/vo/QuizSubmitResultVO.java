package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 在线答题判分结果：包含每题判分明细与整体统计。
 */
@Data
public class QuizSubmitResultVO {

    /** 提交题目总数 */
    private Integer total;

    /** 答对题数 */
    private Integer correct;

    /** 答错题数 */
    private Integer wrong;

    /** 正确率（0-100，四舍五入） */
    private Integer accuracy;

    /** 本次自动同步到错题本的题数 */
    private Integer syncedMistakes;

    /** 每题判分明细 */
    private List<Item> items;

    @Data
    public static class Item {
        private Long questionId;

        private String userAnswer;

        /** 展示用的正确答案文本（选择题已转为选项内容） */
        private String correctAnswer;

        /** 是否答对 */
        private Boolean correct;

        private String explanation;

        /** 本题得分（0-100） */
        private Integer score;
    }
}
