package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.AiConfig;
import com.knowflow.entity.AiConceptDiagram;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.mapper.AiConceptDiagramMapper;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱业务服务：
 * 1. 分类-文档层级图谱（已有）
 * 2. 技术栈依赖图谱（AI 生成）
 * 3. 概念可视化图解（AI 生成 Mermaid）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final DocCategoryMapper categoryMapper;
    private final DocDocumentMapper docMapper;
    private final AiConceptDiagramMapper conceptDiagramMapper;
    private final AiService aiService;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===== 1. 分类-文档层级图谱 =====

    public KnowledgeGraphVO getGraph() {
        List<DocCategory> categories = categoryMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getStatus, 1)
                .orderByAsc(DocCategory::getSortOrder));
        List<DocDocument> docs = docMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocDocument>()
                .eq(DocDocument::getStatus, 1));

        List<GraphNodeVO> nodes = new ArrayList<>();
        List<GraphEdgeVO> edges = new ArrayList<>();

        for (DocCategory c : categories) {
            GraphNodeVO node = new GraphNodeVO();
            node.setId("cat-" + c.getId());
            node.setLabel(c.getName());
            node.setType("category");
            node.setValue(c.getDocCount() != null ? c.getDocCount() : 0);
            nodes.add(node);
            if (c.getParentId() != null && c.getParentId() != 0) {
                GraphEdgeVO edge = new GraphEdgeVO();
                edge.setSource("cat-" + c.getParentId());
                edge.setTarget("cat-" + c.getId());
                edge.setRelation("parent");
                edges.add(edge);
            }
        }
        for (DocDocument d : docs) {
            GraphNodeVO node = new GraphNodeVO();
            node.setId("doc-" + d.getId());
            node.setLabel(d.getTitle());
            node.setType("doc");
            node.setValue(d.getViewCount() != null ? d.getViewCount() : 0);
            nodes.add(node);
            if (d.getCategoryId() != null) {
                GraphEdgeVO edge = new GraphEdgeVO();
                edge.setSource("cat-" + d.getCategoryId());
                edge.setTarget("doc-" + d.getId());
                edge.setRelation("contains");
                edges.add(edge);
            }
        }
        KnowledgeGraphVO graph = new KnowledgeGraphVO();
        graph.setNodes(nodes);
        graph.setEdges(edges);
        return graph;
    }

    // ===== 2. 技术栈依赖图谱 =====

    /**
     * 根据技术主题 + 知识库 ID 生成技术栈依赖图谱。
     * AI 分析该领域涉及的编程语言、框架、数据库、工具、算法等，并构建前置依赖关系。
     */
    public TechGraphVO getTechGraph(String topic, Long categoryId) {
        // 1. 收集该知识库下的文档内容作为 AI 上下文
        List<String> docContents = new ArrayList<>();
        if (categoryId != null) {
            List<DocDocument> docs = docMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocDocument>()
                            .eq(DocDocument::getCategoryId, categoryId)
                            .eq(DocDocument::getStatus, 1)
                            .select(DocDocument::getTitle, DocDocument::getSummary, DocDocument::getContent)
                            .last("LIMIT 15")
            );
            for (DocDocument d : docs) {
                StringBuilder sb = new StringBuilder();
                if (d.getTitle() != null) sb.append(d.getTitle()).append("\n");
                if (d.getSummary() != null) sb.append(d.getSummary()).append("\n");
                if (d.getContent() != null) sb.append(d.getContent(), 0, Math.min(d.getContent().length(), 800));
                docContents.add(sb.toString());
            }
        }

        // 2. 构建 prompt
        String systemPrompt = "你是一位资深的技术架构师，擅长梳理技术栈之间的依赖关系。" +
                "请根据给定的技术主题和相关资料，生成该领域内的技术栈依赖图谱。";

        String userPrompt = buildTechGraphPrompt(topic, docContents);

        // 3. 调用 AI
        String rawResponse;
        try {
            rawResponse = aiService.complete(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("AI 技术栈图谱生成失败，使用预设模板: {}", e.getMessage());
            return buildDefaultTechGraph(topic);
        }

        // 4. 解析 JSON 响应
        TechGraphVO result = parseTechGraphResponse(rawResponse, topic);
        if (result == null || result.getNodes() == null || result.getNodes().isEmpty()) {
            return buildDefaultTechGraph(topic);
        }

        // 5. 关联知识库文档到技术节点
        enrichWithDocRelations(result, categoryId);

        return result;
    }

    private String buildTechGraphPrompt(String topic, List<String> docContents) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为「").append(topic).append("」构建一个技术栈依赖图谱。\n\n");
        sb.append("要求：\n");
        sb.append("1. 识别该领域涉及的核心技术栈，分类为：LANGUAGE（编程语言）/ FRAMEWORK（框架）/ TOOL（工具）/ DATABASE（数据库）/ ALGORITHM（算法）/ PLATFORM（平台）\n");
        sb.append("2. 梳理技术之间的前置依赖关系（如学习 Spring Boot 需先掌握 Java 基础）\n");
        sb.append("3. 以最核心的技术为中心节点，展示 2-3 层依赖关系\n");
        sb.append("4. 为每个技术标注难度（1=入门, 2=中等, 3=进阶）\n\n");

        if (!docContents.isEmpty()) {
            sb.append("以下是知识库中与该主题相关的参考资料，请参考这些内容：\n\n");
            for (int i = 0; i < Math.min(docContents.size(), 5); i++) {
                sb.append("【资料").append(i + 1).append("】\n").append(docContents.get(i)).append("\n\n");
            }
        }

        sb.append("请严格按照以下 JSON 格式输出（不要输出其他文字）：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"nodes\": [\n");
        sb.append("    {\"id\": \"tech-1\", \"name\": \"Java\", \"category\": \"LANGUAGE\", \"categoryLabel\": \"编程语言\", \"description\": \"...\", \"difficulty\": 1},\n");
        sb.append("    {\"id\": \"tech-2\", \"name\": \"Spring Boot\", \"category\": \"FRAMEWORK\", \"categoryLabel\": \"框架\", \"description\": \"...\", \"difficulty\": 2}\n");
        sb.append("  ],\n");
        sb.append("  \"edges\": [\n");
        sb.append("    {\"source\": \"tech-1\", \"target\": \"tech-2\", \"relation\": \"PREREQUISITE\", \"strength\": 3, \"description\": \"学习 Spring Boot 需先掌握 Java\"}\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("```");
        return sb.toString();
    }

    private TechGraphVO parseTechGraphResponse(String raw, String topic) {
        try {
            // 尝试提取 JSON
            String json = extractJson(raw);
            if (json == null) return null;

            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});

            List<TechNodeVO> nodes = new ArrayList<>();
            List<Map<String, Object>> nodeList = (List<Map<String, Object>>) map.get("nodes");
            if (nodeList != null) {
                for (Map<String, Object> nm : nodeList) {
                    TechNodeVO node = new TechNodeVO();
                    node.setId((String) nm.getOrDefault("id", ""));
                    node.setName((String) nm.getOrDefault("name", ""));
                    node.setCategory((String) nm.getOrDefault("category", "TOOL"));
                    node.setCategoryLabel(getCategoryLabel(node.getCategory()));
                    node.setDescription((String) nm.getOrDefault("description", ""));
                    Object diff = nm.get("difficulty");
                    node.setDifficulty(diff != null ? ((Number) diff).intValue() : 1);
                    node.setDocCount(0);
                    nodes.add(node);
                }
            }

            List<TechEdgeVO> edges = new ArrayList<>();
            List<Map<String, Object>> edgeList = (List<Map<String, Object>>) map.get("edges");
            if (edgeList != null) {
                for (Map<String, Object> em : edgeList) {
                    TechEdgeVO edge = new TechEdgeVO();
                    edge.setSource((String) em.getOrDefault("source", ""));
                    edge.setTarget((String) em.getOrDefault("target", ""));
                    edge.setRelation((String) em.getOrDefault("relation", "DEPENDS"));
                    Object str = em.get("strength");
                    edge.setStrength(str != null ? ((Number) str).intValue() : 1);
                    edge.setDescription((String) em.getOrDefault("description", ""));
                    edges.add(edge);
                }
            }

            TechGraphVO vo = new TechGraphVO();
            vo.setTopic(topic);
            vo.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            vo.setNodes(nodes);
            vo.setEdges(edges);
            return vo;
        } catch (Exception e) {
            log.warn("AI 技术栈图谱 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private TechGraphVO buildDefaultTechGraph(String topic) {
        TechGraphVO vo = new TechGraphVO();
        vo.setTopic(topic);
        vo.setGeneratedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        vo.setNodes(new ArrayList<>());
        vo.setEdges(new ArrayList<>());

        // 根据关键词给出预设
        String lower = topic.toLowerCase();
        if (lower.contains("spring") || lower.contains("java")) {
            vo.setNodes(buildJavaPresetNodes());
            vo.setEdges(buildJavaPresetEdges());
        } else if (lower.contains("python") || lower.contains("django") || lower.contains("flask")) {
            vo.setNodes(buildPythonPresetNodes());
            vo.setEdges(buildPythonPresetEdges());
        } else if (lower.contains("ai") || lower.contains("ml") || lower.contains("machine") || lower.contains("深度学习")) {
            vo.setNodes(buildAIPresetNodes());
            vo.setEdges(buildAIPresetEdges());
        } else if (lower.contains("前端") || lower.contains("vue") || lower.contains("react") || lower.contains("javascript")) {
            vo.setNodes(buildFrontendPresetNodes());
            vo.setEdges(buildFrontendPresetEdges());
        } else {
            // 通用预设
            vo.setNodes(buildGenericPresetNodes(topic));
            vo.setEdges(buildGenericPresetEdges());
        }
        return vo;
    }

    private void enrichWithDocRelations(TechGraphVO graph, Long categoryId) {
        if (categoryId == null || graph.getNodes() == null) return;
        List<DocDocument> docs = docMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocDocument>()
                        .eq(DocDocument::getCategoryId, categoryId)
                        .eq(DocDocument::getStatus, 1)
                        .select(DocDocument::getTitle, DocDocument::getTags)
        );

        for (TechNodeVO node : graph.getNodes()) {
            int count = 0;
            for (DocDocument doc : docs) {
                String titleLower = doc.getTitle() != null ? doc.getTitle().toLowerCase() : "";
                String tagsLower = doc.getTags() != null ? doc.getTags().toLowerCase() : "";
                String nodeName = node.getName().toLowerCase();
                if (titleLower.contains(nodeName) || tagsLower.contains(nodeName)
                        || titleLower.contains(nodeName.replace(" ", ""))) {
                    count++;
                }
            }
            node.setDocCount(count);
        }
    }

    private String getCategoryLabel(String category) {
        return switch (category != null ? category : "TOOL") {
            case "LANGUAGE" -> "编程语言";
            case "FRAMEWORK" -> "框架";
            case "TOOL" -> "工具";
            case "DATABASE" -> "数据库";
            case "ALGORITHM" -> "算法";
            case "PLATFORM" -> "平台";
            default -> "其他";
        };
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // 去除 markdown 代码块标记
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        // 找到第一个 { 和最后一个 }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < 0 || start >= end) return null;
        return cleaned.substring(start, end + 1);
    }

    // ===== 3. 概念可视化图解（支持持久化缓存） =====

    /**
     * 获取概念图解：优先查数据库缓存，未命中则调用 AI 生成并持久化。
     * @param userId  当前用户ID
     * @param concept 概念名称
     */
    public ConceptDiagramVO getConceptDiagram(Long userId, String concept) {
        // 1. 查询缓存
        if (userId != null) {
            AiConceptDiagram cached = conceptDiagramMapper.selectOne(
                    new LambdaQueryWrapper<AiConceptDiagram>()
                            .eq(AiConceptDiagram::getUserId, userId)
                            .eq(AiConceptDiagram::getConcept, concept)
                            .last("LIMIT 1"));
            if (cached != null) {
                return entityToVo(cached);
            }
        }
        // 2. 未命中缓存，生成新的
        return generateAndSaveConceptDiagram(userId, concept);
    }

    /**
     * 重新生成概念图解：先删除旧缓存，再调用 AI 生成新结果并保存。
     */
    public ConceptDiagramVO regenerateConceptDiagram(Long userId, String concept) {
        if (userId != null) {
            // 逻辑删除旧记录（由 @TableLogic 自动处理）
            conceptDiagramMapper.delete(new LambdaQueryWrapper<AiConceptDiagram>()
                    .eq(AiConceptDiagram::getUserId, userId)
                    .eq(AiConceptDiagram::getConcept, concept));
        }
        return generateAndSaveConceptDiagram(userId, concept);
    }

    private ConceptDiagramVO generateAndSaveConceptDiagram(Long userId, String concept) {
        String systemPrompt = "你是一位擅长用图解方式讲解编程与 AI 概念的专家。" +
                "请用 Mermaid 语法绘制图解，帮助初学者快速理解概念。" +
                "除了图解，还需提供关键知识点、关联概念、难度评级和代码示例，形成完整的概念学习体验。";

        String userPrompt = buildConceptDiagramPrompt(concept);

        String rawResponse;
        try {
            rawResponse = aiService.complete(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("AI 概念图解生成失败: {}", e.getMessage());
            return buildDefaultConceptDiagram(concept);
        }

        ConceptDiagramVO vo = parseConceptDiagramResponse(rawResponse, concept);

        // 持久化到数据库
        if (userId != null && vo != null) {
            try {
                AiConceptDiagram entity = voToEntity(userId, vo);
                conceptDiagramMapper.insert(entity);
            } catch (Exception e) {
                log.warn("概念图解缓存保存失败: {}", e.getMessage());
            }
        }
        return vo;
    }

    /** 实体转 VO（反序列化 JSON 字段）。 */
    @SuppressWarnings("unchecked")
    private ConceptDiagramVO entityToVo(AiConceptDiagram e) {
        ConceptDiagramVO vo = new ConceptDiagramVO();
        vo.setConcept(e.getConcept());
        vo.setDiagramType(e.getDiagramType());
        vo.setMermaidCode(e.getMermaidCode());
        vo.setDescription(e.getDescription());
        vo.setExplanation(e.getExplanation());
        vo.setDifficulty(e.getDifficulty());
        vo.setCodeExample(e.getCodeExample());
        try {
            if (e.getKeyPoints() != null && !e.getKeyPoints().isBlank()) {
                vo.setKeyPoints(objectMapper.readValue(e.getKeyPoints(), new TypeReference<List<String>>() {}));
            }
        } catch (Exception ex) {
            vo.setKeyPoints(Collections.emptyList());
        }
        try {
            if (e.getRelatedConcepts() != null && !e.getRelatedConcepts().isBlank()) {
                vo.setRelatedConcepts(objectMapper.readValue(e.getRelatedConcepts(), new TypeReference<List<String>>() {}));
            }
        } catch (Exception ex) {
            vo.setRelatedConcepts(Collections.emptyList());
        }
        return vo;
    }

    /** VO 转实体（序列化 JSON 字段）。 */
    private AiConceptDiagram voToEntity(Long userId, ConceptDiagramVO vo) throws Exception {
        AiConceptDiagram e = new AiConceptDiagram();
        e.setUserId(userId);
        e.setConcept(vo.getConcept());
        e.setDiagramType(vo.getDiagramType());
        e.setMermaidCode(vo.getMermaidCode());
        e.setDescription(vo.getDescription());
        e.setExplanation(vo.getExplanation());
        e.setDifficulty(vo.getDifficulty());
        e.setCodeExample(vo.getCodeExample());
        e.setKeyPoints(vo.getKeyPoints() != null ? objectMapper.writeValueAsString(vo.getKeyPoints()) : "[]");
        e.setRelatedConcepts(vo.getRelatedConcepts() != null ? objectMapper.writeValueAsString(vo.getRelatedConcepts()) : "[]");
        return e;
    }

    private String buildConceptDiagramPrompt(String concept) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为概念「").append(concept).append("」绘制一个可视化图解，并提供完整的学习信息。\n\n");
        sb.append("要求：\n");
        sb.append("1. 选择最合适的 Mermaid 图表类型：\n");
        sb.append("   - FLOWCHART：解释流程/逻辑/关系，最常用\n");
        sb.append("   - SEQUENCE：解释交互时序\n");
        sb.append("   - CLASS：解释类结构（OOP 概念）\n");
        sb.append("   - ER：解释数据模型\n");
        sb.append("   - PIE：解释比例分布\n");
        sb.append("2. 图解应简洁清晰，不超过 15 个节点\n");
        sb.append("3. 使用中文标签\n");
        sb.append("4. 确保语法正确可直接渲染\n");
        sb.append("5. 提取 3-5 个关键知识点（keyPoints）\n");
        sb.append("6. 推荐 3-5 个关联概念（relatedConcepts）供拓展学习\n");
        sb.append("7. 评估概念难度：1 入门 / 2 中等 / 3 进阶\n");
        sb.append("8. 如适用，提供简洁的代码示例（codeExample）\n\n");

        sb.append("请严格按以下 JSON 格式输出（不要输出其他文字）：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"diagramType\": \"FLOWCHART\",\n");
        sb.append("  \"mermaidCode\": \"flowchart TD\\n  A[开始] --> B{条件}\\n  B -->|是| C[结果1]\\n  B -->|否| D[结果2]\",\n");
        sb.append("  \"description\": \"概念的一句话说明\",\n");
        sb.append("  \"explanation\": \"详细解释（200-400字），从定义、原理、应用场景等角度展开\",\n");
        sb.append("  \"difficulty\": 1,\n");
        sb.append("  \"keyPoints\": [\"要点1\", \"要点2\", \"要点3\"],\n");
        sb.append("  \"relatedConcepts\": [\"关联概念1\", \"关联概念2\", \"关联概念3\"],\n");
        sb.append("  \"codeExample\": \"# 代码示例（如不适用则为空字符串）\\nprint('hello')\"\n");
        sb.append("}\n");
        sb.append("```");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private ConceptDiagramVO parseConceptDiagramResponse(String raw, String concept) {
        try {
            String json = extractJson(raw);
            if (json == null) return buildDefaultConceptDiagram(concept);

            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});

            ConceptDiagramVO vo = new ConceptDiagramVO();
            vo.setConcept(concept);
            vo.setDiagramType((String) map.getOrDefault("diagramType", "FLOWCHART"));
            vo.setMermaidCode((String) map.getOrDefault("mermaidCode", ""));
            vo.setDescription((String) map.getOrDefault("description", ""));
            vo.setExplanation((String) map.getOrDefault("explanation", ""));

            // 难度
            Object diff = map.get("difficulty");
            vo.setDifficulty(diff instanceof Number ? ((Number) diff).intValue() : 1);

            // 关键知识点
            Object kp = map.get("keyPoints");
            if (kp instanceof List) {
                vo.setKeyPoints(((List<Object>) kp).stream()
                        .map(String::valueOf).collect(java.util.stream.Collectors.toList()));
            }

            // 关联概念
            Object rc = map.get("relatedConcepts");
            if (rc instanceof List) {
                vo.setRelatedConcepts(((List<Object>) rc).stream()
                        .map(String::valueOf).collect(java.util.stream.Collectors.toList()));
            }

            // 代码示例
            vo.setCodeExample((String) map.getOrDefault("codeExample", ""));

            if (vo.getMermaidCode() == null || vo.getMermaidCode().isBlank()) {
                return buildDefaultConceptDiagram(concept);
            }
            return vo;
        } catch (Exception e) {
            log.warn("概念图解 JSON 解析失败: {}", e.getMessage());
            return buildDefaultConceptDiagram(concept);
        }
    }

    private ConceptDiagramVO buildDefaultConceptDiagram(String concept) {
        ConceptDiagramVO vo = new ConceptDiagramVO();
        vo.setConcept(concept);
        vo.setDiagramType("FLOWCHART");
        vo.setMermaidCode("flowchart TD\n  A[" + concept + "] --> B(核心要点1)\n  A --> C(核心要点2)\n  A --> D(核心要点3)");
        vo.setDescription("「" + concept + "」的概念图解");
        vo.setExplanation("这是关于「" + concept + "」的基础概念图解。AI 服务未配置时生成的默认图解，配置后可获取更精确的可视化。");
        vo.setDifficulty(1);
        vo.setKeyPoints(List.of("要点1", "要点2", "要点3"));
        vo.setRelatedConcepts(List.of());
        vo.setCodeExample("");
        return vo;
    }

    // ===== 预设数据 =====

    private List<TechNodeVO> buildJavaPresetNodes() {
        List<TechNodeVO> nodes = new ArrayList<>();
        addNode(nodes, "java-basic", "Java 基础", "LANGUAGE", "编程语言", "变量、循环、面向对象基础", 1);
        addNode(nodes, "java-oop", "Java 面向对象", "LANGUAGE", "编程语言", "封装、继承、多态", 2);
        addNode(nodes, "java-io", "Java IO/NIO", "LANGUAGE", "编程语言", "文件操作、网络编程", 2);
        addNode(nodes, "maven", "Maven", "TOOL", "构建工具", "依赖管理与项目构建", 1);
        addNode(nodes, "spring", "Spring", "FRAMEWORK", "框架", "IoC、AOP、事务", 2);
        addNode(nodes, "spring-boot", "Spring Boot", "FRAMEWORK", "框架", "自动配置、起步依赖", 2);
        addNode(nodes, "spring-mvc", "Spring MVC", "FRAMEWORK", "框架", "Web 开发、RESTful API", 2);
        addNode(nodes, "mybatis", "MyBatis", "FRAMEWORK", "持久层框架", "ORM 映射、动态 SQL", 2);
        addNode(nodes, "mysql", "MySQL", "DATABASE", "数据库", "关系型数据库", 1);
        addNode(nodes, "redis", "Redis", "DATABASE", "缓存数据库", "内存缓存、分布式锁", 2);
        addNode(nodes, "jwt", "JWT", "TOOL", "认证工具", "无状态身份认证", 2);
        addNode(nodes, "docker", "Docker", "TOOL", "容器工具", "容器化部署", 2);
        return nodes;
    }

    private List<TechEdgeVO> buildJavaPresetEdges() {
        List<TechEdgeVO> edges = new ArrayList<>();
        addEdge(edges, "java-basic", "java-oop", "PREREQUISITE", 3, "掌握基础后学面向对象");
        addEdge(edges, "java-basic", "java-io", "PREREQUISITE", 2, "基础语法是 IO 编程前提");
        addEdge(edges, "java-oop", "spring", "PREREQUISITE", 3, "Spring 核心基于 OOP");
        addEdge(edges, "maven", "spring-boot", "PREREQUISITE", 2, "Spring Boot 项目用 Maven 构建");
        addEdge(edges, "spring", "spring-boot", "COMPONENT", 3, "Spring Boot 集成 Spring");
        addEdge(edges, "spring", "spring-mvc", "COMPONENT", 3, "Spring MVC 是 Spring 的一部分");
        addEdge(edges, "spring-boot", "spring-mvc", "COMPONENT", 2, "Spring Boot 包含 MVC");
        addEdge(edges, "spring-mvc", "mybatis", "DEPENDS", 2, "常与 MyBatis 配合");
        addEdge(edges, "mybatis", "mysql", "DEPENDS", 3, "MyBatis 操作 MySQL");
        addEdge(edges, "spring", "redis", "DEPENDS", 2, "Spring 常集成 Redis");
        addEdge(edges, "spring-boot", "jwt", "DEPENDS", 2, "Spring Boot 常用 JWT 认证");
        addEdge(edges, "spring-boot", "docker", "DEPENDS", 2, "Spring Boot 应用容器化部署");
        return edges;
    }

    private List<TechNodeVO> buildPythonPresetNodes() {
        List<TechNodeVO> nodes = new ArrayList<>();
        addNode(nodes, "py-basic", "Python 基础", "LANGUAGE", "编程语言", "语法、数据类型、控制流", 1);
        addNode(nodes, "py-oop", "Python OOP", "LANGUAGE", "编程语言", "类、继承、魔法方法", 2);
        addNode(nodes, "py-pip", "pip", "TOOL", "包管理工具", "包安装与管理", 1);
        addNode(nodes, "flask", "Flask", "FRAMEWORK", "Web 框架", "轻量级 Web 框架", 2);
        addNode(nodes, "django", "Django", "FRAMEWORK", "Web 框架", "全栈 Web 框架", 2);
        addNode(nodes, "fastapi", "FastAPI", "FRAMEWORK", "Web 框架", "现代异步 Web 框架", 2);
        addNode(nodes, "sqlalchemy", "SQLAlchemy", "FRAMEWORK", "ORM 框架", "Python ORM", 2);
        addNode(nodes, "postgres", "PostgreSQL", "DATABASE", "数据库", "关系型数据库", 1);
        addNode(nodes, "celery", "Celery", "FRAMEWORK", "任务队列", "异步任务队列", 2);
        return nodes;
    }

    private List<TechEdgeVO> buildPythonPresetEdges() {
        List<TechEdgeVO> edges = new ArrayList<>();
        addEdge(edges, "py-basic", "py-oop", "PREREQUISITE", 3, "基础语法是 OOP 前提");
        addEdge(edges, "py-basic", "py-pip", "PREREQUISITE", 1, "会用 pip 安装包");
        addEdge(edges, "py-pip", "flask", "PREREQUISITE", 1, "用 pip 安装 Flask");
        addEdge(edges, "py-pip", "django", "PREREQUISITE", 1, "用 pip 安装 Django");
        addEdge(edges, "py-oop", "flask", "PREREQUISITE", 2, "Flask 用到类装饰器");
        addEdge(edges, "py-oop", "django", "PREREQUISITE", 3, "Django 大量使用 OOP");
        addEdge(edges, "flask", "fastapi", "COMPONENT", 1, "FastAPI 借鉴 Flask 设计");
        addEdge(edges, "django", "sqlalchemy", "COMPONENT", 2, "Django 可配合 SQLAlchemy");
        addEdge(edges, "flask", "sqlalchemy", "COMPONENT", 2, "Flask 常用 SQLAlchemy");
        addEdge(edges, "sqlalchemy", "postgres", "DEPENDS", 3, "SQLAlchemy 操作 PostgreSQL");
        addEdge(edges, "django", "celery", "DEPENDS", 2, "Django 常集成 Celery");
        return edges;
    }

    private List<TechNodeVO> buildAIPresetNodes() {
        List<TechNodeVO> nodes = new ArrayList<>();
        addNode(nodes, "py-basic-ai", "Python 基础", "LANGUAGE", "编程语言", "AI 开发必备语言", 1);
        addNode(nodes, "numpy", "NumPy", "TOOL", "数值计算", "矩阵运算基础库", 1);
        addNode(nodes, "pandas", "Pandas", "TOOL", "数据分析", "数据处理与分析", 1);
        addNode(nodes, "sklearn", "scikit-learn", "FRAMEWORK", "机器学习框架", "传统 ML 算法", 2);
        addNode(nodes, "pytorch", "PyTorch", "FRAMEWORK", "深度学习框架", "动态图深度学习", 3);
        addNode(nodes, "tensorflow", "TensorFlow", "FRAMEWORK", "深度学习框架", "静态图深度学习", 3);
        addNode(nodes, "transformers", "Transformers", "FRAMEWORK", "NLP 框架", "预训练模型库", 3);
        addNode(nodes, "rag", "RAG", "ALGORITHM", "检索增强生成", "向量检索 + LLM", 3);
        addNode(nodes, "embedding", "Embedding", "ALGORITHM", "嵌入算法", "文本向量化", 2);
        addNode(nodes, "vector-db", "向量数据库", "DATABASE", "向量存储", "Milvus/Pgvector", 2);
        addNode(nodes, "llm", "大语言模型", "PLATFORM", "AI 平台", "LLM 应用开发", 3);
        return nodes;
    }

    private List<TechEdgeVO> buildAIPresetEdges() {
        List<TechEdgeVO> edges = new ArrayList<>();
        addEdge(edges, "py-basic-ai", "numpy", "PREREQUISITE", 3, "AI 开发必备 NumPy");
        addEdge(edges, "py-basic-ai", "pandas", "PREREQUISITE", 3, "数据处理必备 Pandas");
        addEdge(edges, "numpy", "sklearn", "PREREQUISITE", 3, "scikit-learn 依赖 NumPy");
        addEdge(edges, "numpy", "pytorch", "PREREQUISITE", 3, "PyTorch 底层依赖 NumPy");
        addEdge(edges, "numpy", "tensorflow", "PREREQUISITE", 3, "TensorFlow 依赖 NumPy");
        addEdge(edges, "pandas", "sklearn", "COMPONENT", 2, "数据预处理配合使用");
        addEdge(edges, "sklearn", "pytorch", "COMPONENT", 1, "传统 ML 到深度学习");
        addEdge(edges, "pytorch", "transformers", "PREREQUISITE", 3, "Transformers 基于 PyTorch");
        addEdge(edges, "transformers", "llm", "COMPONENT", 3, "Transformers 提供 LLM 能力");
        addEdge(edges, "embedding", "vector-db", "PREREQUISITE", 3, "Embedding 存入向量库");
        addEdge(edges, "vector-db", "rag", "PREREQUISITE", 3, "向量检索是 RAG 核心");
        addEdge(edges, "llm", "rag", "COMPONENT", 3, "RAG = 检索 + LLM 生成");
        return edges;
    }

    private List<TechNodeVO> buildFrontendPresetNodes() {
        List<TechNodeVO> nodes = new ArrayList<>();
        addNode(nodes, "html", "HTML", "LANGUAGE", "标记语言", "网页结构", 1);
        addNode(nodes, "css", "CSS", "LANGUAGE", "样式语言", "网页样式", 1);
        addNode(nodes, "js", "JavaScript", "LANGUAGE", "编程语言", "网页交互", 1);
        addNode(nodes, "ts", "TypeScript", "LANGUAGE", "编程语言", "JS 超集，类型安全", 2);
        addNode(nodes, "vue", "Vue.js", "FRAMEWORK", "前端框架", "渐进式前端框架", 2);
        addNode(nodes, "react", "React", "FRAMEWORK", "前端框架", "声明式 UI 框架", 2);
        addNode(nodes, "vite", "Vite", "TOOL", "构建工具", "新一代前端构建", 2);
        addNode(nodes, "webpack", "Webpack", "TOOL", "构建工具", "传统打包工具", 2);
        addNode(nodes, "node", "Node.js", "PLATFORM", "运行时", "JS 服务端运行时", 2);
        addNode(nodes, "npm", "npm/yarn", "TOOL", "包管理", "前端包管理", 1);
        return nodes;
    }

    private List<TechEdgeVO> buildFrontendPresetEdges() {
        List<TechEdgeVO> edges = new ArrayList<>();
        addEdge(edges, "html", "js", "PREREQUISITE", 3, "HTML 是 JS 载体");
        addEdge(edges, "css", "js", "COMPONENT", 1, "CSS 与 JS 配合");
        addEdge(edges, "js", "ts", "PREREQUISITE", 3, "TS 是 JS 的超集");
        addEdge(edges, "js", "vue", "PREREQUISITE", 3, "Vue 基于 JS");
        addEdge(edges, "ts", "vue", "COMPONENT", 2, "Vue 支持 TS");
        addEdge(edges, "js", "react", "PREREQUISITE", 3, "React 基于 JS");
        addEdge(edges, "ts", "react", "COMPONENT", 2, "React 支持 TS");
        addEdge(edges, "npm", "vite", "PREREQUISITE", 1, "用 npm 装 Vite");
        addEdge(edges, "node", "vite", "PREREQUISITE", 3, "Vite 需要 Node.js");
        addEdge(edges, "vue", "vite", "COMPONENT", 3, "Vite 是 Vue 推荐构建工具");
        addEdge(edges, "react", "vite", "COMPONENT", 2, "Vite 支持 React");
        addEdge(edges, "node", "webpack", "PREREQUISITE", 3, "Webpack 基于 Node.js");
        return edges;
    }

    private List<TechNodeVO> buildGenericPresetNodes(String topic) {
        List<TechNodeVO> nodes = new ArrayList<>();
        addNode(nodes, "core", topic + " 核心", "FRAMEWORK", "核心技术", "该领域的核心技术栈", 2);
        addNode(nodes, "basic", "基础前置知识", "LANGUAGE", "编程语言", "进入该领域需掌握的基础知识", 1);
        addNode(nodes, "tool", "常用工具", "TOOL", "开发工具", "该领域的常用开发工具", 2);
        addNode(nodes, "db", "配套数据库", "DATABASE", "数据库", "该领域常用的数据库", 2);
        addNode(nodes, "deploy", "部署与运维", "PLATFORM", "部署平台", "应用部署与运维相关技术", 2);
        return nodes;
    }

    private List<TechEdgeVO> buildGenericPresetEdges() {
        List<TechEdgeVO> edges = new ArrayList<>();
        addEdge(edges, "basic", "core", "PREREQUISITE", 3, "基础是核心技术的前提");
        addEdge(edges, "core", "tool", "DEPENDS", 2, "核心技术需要工具支持");
        addEdge(edges, "core", "db", "DEPENDS", 2, "核心技术常配套数据库");
        addEdge(edges, "core", "deploy", "DEPENDS", 2, "应用需要部署与运维");
        return edges;
    }

    private void addNode(List<TechNodeVO> nodes, String id, String name, String category, String categoryLabel, String desc, int diff) {
        TechNodeVO node = new TechNodeVO();
        node.setId(id);
        node.setName(name);
        node.setCategory(category);
        node.setCategoryLabel(categoryLabel);
        node.setDescription(desc);
        node.setDifficulty(diff);
        node.setDocCount(0);
        nodes.add(node);
    }

    private void addEdge(List<TechEdgeVO> edges, String source, String target, String relation, int strength, String desc) {
        TechEdgeVO edge = new TechEdgeVO();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setRelation(relation);
        edge.setStrength(strength);
        edge.setDescription(desc);
        edges.add(edge);
    }
}
