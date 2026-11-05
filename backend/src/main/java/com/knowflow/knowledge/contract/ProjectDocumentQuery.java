package com.knowflow.knowledge.contract;

/** 由知识模块所有者授权、供项目上下文快照使用的只读文档资料。 */
public interface ProjectDocumentQuery {
    /** 完成现有文档访问校验后读取当前内容；不存在或已删除返回 404，不可读保留 401/403；不评分、不验证项目代码、不持久化。 */
    Content requireReadable(long documentId, long actor);

    /** 保留来源字段的空值，由消费快照自行限制展示范围。 */
    record Content(String title, String content) {}
}
