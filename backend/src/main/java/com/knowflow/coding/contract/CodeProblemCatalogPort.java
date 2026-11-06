package com.knowflow.coding.contract;

/**
 * 算法题（CODE 题型）对目录模块开放的编辑与发布契约。
 *
 * <p>只暴露管理端编辑所需的两个能力：读取编辑投影、切换发布状态。题目实体与 Mapper 仍由 coding 模块独占，调用方不得借此获得持久化访问权。
 */
public interface CodeProblemCatalogPort {

  /**
   * 管理端编辑投影。
   *
   * <p>返回类型刻意保持为 {@link Object}：投影结构由 coding 模块决定并按既有 JSON 契约序列化， 目录模块只做透传与指纹计算，不解析字段，避免形成对 coding
   * 内部 VO 的编译期依赖。
   *
   * @param questionId 题目主键（quiz_question.id）
   * @return 来源模块的编辑投影
   */
  Object adminDetail(Long questionId);

  /**
   * 发布或下架算法题。
   *
   * <p>发布前的内容安全校验（如 AI 候选题必须人工审核）由 coding 模块自行保证， 调用方不需要也无法绕过。
   *
   * @param questionId 题目主键
   * @param published true 发布，false 下架
   */
  void publish(Long questionId, boolean published);
}
