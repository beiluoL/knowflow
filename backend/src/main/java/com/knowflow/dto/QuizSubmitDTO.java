package com.knowflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 在线答题提交请求：一次提交可包含多道题的作答。
 */
@Data
public class QuizSubmitDTO {

    private List<Item> answers;

    @Data
    public static class Item {
        /** 题目ID */
        private Long questionId;

        /** 用户答案：选择题为选项索引（"0" / "0,2"），判断题为 true/false，其余为文本 */
        private String userAnswer;

        /** 答题耗时（秒），可选 */
        private Integer timeCost;
    }
}
