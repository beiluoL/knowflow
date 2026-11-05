package com.knowflow.coding.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.coding.contract.LegacyCodeCatalogQueryPort;
import com.knowflow.coding.mapper.CodeQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** 在 Coding 边界内读取旧版题目，保留既有管理端 JSON 合同。 */
@Service
@RequiredArgsConstructor
public class LegacyCodeCatalogQueryService implements LegacyCodeCatalogQueryPort {

    private final CodeQuestionMapper questions;
    private final ObjectMapper objectMapper;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public JsonNode findDetail(long questionId) {
        var question = questions.selectById(questionId);
        return question == null ? null : objectMapper.valueToTree(question);
    }
}
