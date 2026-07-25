package com.knowflow.service;

import com.knowflow.entity.DocDocument;

import java.util.List;

public interface AiService {

    String chat(String userMessage, List<DocDocument> contextDocs);

    boolean isConfigured();
}
