package com.knowflow.service;

import com.knowflow.dto.KnowledgeProcessAcceptRequestDTO;
import com.knowflow.dto.KnowledgeProcessRequestDTO;
import com.knowflow.vo.KnowledgeAcceptResultVO;
import com.knowflow.vo.KnowledgeProcessResultVO;

/**
 * Architecture-4.3：Knowledge Processing 2.0 服务。
 *
 * <p>职责边界（本阶段最重要的产品原则）：</p>
 * <ul>
 *   <li>AI 只负责「理解文本 → 产出结构化候选」；</li>
 *   <li>Core（校验、去重、关系验证、落库、节点关联）全部是确定性规则代码；</li>
 *   <li>process() 零写库，只返回瞬态候选；只有 accept() 且用户显式接受后才写入 Knowledge Core；</li>
 *   <li>不污染 Mastery / Review / UserApp / Goal / Learning Path（本服务不改写任何学习状态）。</li>
 * </ul>
 */
public interface KnowledgeProcessingService {

    /** 处理一份资料，产出候选（不写库）。userId 用于 AiService 按用户 Key 路由（数据隔离于用户级配置）。 */
    KnowledgeProcessResultVO process(KnowledgeProcessRequestDTO request, Long userId);

    /** 接受候选：确定性校验后落库并（可选）关联学习节点。幂等：重复接受不会产生重复实体/关系/映射。 */
    KnowledgeAcceptResultVO accept(KnowledgeProcessAcceptRequestDTO request, Long userId);
}
