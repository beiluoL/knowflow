package com.knowflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 费曼表达评分提交入参（Architecture-4.0-7）。
 * <p>
 * checkedCriterionIds 为作者勾选的准则 id（如 "accuracy-1"），未知 id 由服务端忽略；
 * 计分口径完全确定性（勾选数 / 该维总准则数），不依赖任何 AI。
 */
@Data
public class FeynmanSubmitDTO {

    /** 表达主题（必填，≤200 字符，如「JVM 年轻代 GC 流程」） */
    private String topic;

    /** 费曼式解释正文（必填，1~10000 字符，目标是讲给一个完全不懂的人听） */
    private String explanation;

    /** 已勾选的自检准则 id 集合（允许空；服务端按 rubric 白名单过滤） */
    private List<String> checkedCriterionIds = new ArrayList<>();

    /**
     * Architecture-4.2 §9：本次表达所属知识点 ID（可选，逻辑外键 kg_entity.id）。
     * 由页面从 Knowledge Context 透传；为空则不写入事件 metadata（禁止服务端回退猜测）。
     */
    private Long knowledgeId;

    /** 作答耗时（秒，可选） */
    private Integer durationSeconds;
}
