package com.knowflow.learning.contract;

import com.fasterxml.jackson.databind.JsonNode;

/** 学习题库的管理目录详情投影，保留现有 JSON 和编辑指纹。 */
public interface LearningQuestionCatalogQueryPort {

    /** 读取普通题或缺少算法扩展的题库记录，逻辑删除记录不可读。 */
    JsonNode findQuiz(long questionId);

    /** 读取场景题，逻辑删除记录不可读。 */
    JsonNode findScenario(long scenarioId);
}
