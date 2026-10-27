package com.knowflow.service;

import com.knowflow.dto.LearningContentAcceptRequest;
import com.knowflow.dto.LearningContentGenerateRequest;
import com.knowflow.vo.LearningContentAcceptResultVO;
import com.knowflow.vo.LearningContentGenerationResultVO;

/**
 * AI 学习内容生成服务（Architecture-4.4 Learning Content Generator）。
 *
 * <p>职责：Knowledge（kg_entity）→ Flashcard/Feynman/Scenario Draft（内存 DTO，零写库）→
 * 用户审核 → accept 写入既有正式实体。边界（§35/§36/§37/§49）：
 * <ul>
 *   <li>generate/accept 全程不产生 LearningEvent、不改写 Mastery、不动 Goal/Learning Path/UserApp/Sidebar；</li>
 *   <li>accept 只写 learning_flashcard / learning_scenario（复用既有模型），不新建任何「xxx2」表；</li>
 *   <li>费曼 Draft 无持久化实体（现有费曼为事件式表达），前端预填跳转表达，不经过 accept 写库；</li>
 *   <li>场景 keywords 服务端持有（draftToken 一次性换取），不下发前端。</li>
 * </ul>
 */
public interface LearningContentService {

    /** Small source-bound study-pack drafts; no invented KG identity or mastery mapping. */
    java.util.Map<String,Object> generateFromDocument(Long docId,String kind,Long userId);

    /** AI 生成三类 Draft（默认三种全生成；非法 JSON 自动重试一次，仍失败明确报错，绝不保存假数据）。 */
    LearningContentGenerationResultVO generate(LearningContentGenerateRequest request, Long userId);

    /** 接受审核后的内容：确定性校验 + 去重 + 落库（幂等：重复接受不产生重复正式内容）。 */
    LearningContentAcceptResultVO accept(LearningContentAcceptRequest request, Long userId);
}
