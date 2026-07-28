package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.vo.GraphEdgeVO;
import com.knowflow.vo.GraphNodeVO;
import com.knowflow.vo.KnowledgeGraphVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识图谱业务服务：以分类为节点、文档为节点，构建分类层级与「分类-文档」归属关系图。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final DocCategoryMapper categoryMapper;

    private final DocDocumentMapper docMapper;

    public KnowledgeGraphVO getGraph() {
        List<DocCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getStatus, 1)
                .orderByAsc(DocCategory::getSortOrder));
        List<DocDocument> docs = docMapper.selectList(new LambdaQueryWrapper<DocDocument>()
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
}
