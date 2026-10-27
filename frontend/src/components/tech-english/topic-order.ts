// 专题学习的领域顺序与一句话说明。
// 只做展示排序与文案，不改变后端 domain 枚举，也不写入任何学习路径数据。
export const DOMAIN_ORDER: string[] = [
  'JAVA',
  'JVM',
  'CONCURRENCY',
  'SPRING',
  'DB',
  'CACHE',
  'MQ',
  'DISTRIBUTED',
  'LLM',
  'RAG',
  'AGENT',
  'AI_ENG',
]

export const DOMAIN_DESCRIPTIONS: Record<string, string> = {
  JAVA: '语言基础与集合：从类型、泛型到集合实现，面试与源码阅读的公共底座。',
  JVM: '类加载、内存结构、垃圾回收与性能诊断的英文表达。',
  CONCURRENCY: '线程、锁、可见性、CAS 与并发工具的术语链路。',
  SPRING: 'IoC、AOP、事务、Bean 生命周期与 Spring Boot 自动配置。',
  DB: '关系模型、索引、事务隔离、SQL 优化与数据库设计。',
  CACHE: '缓存策略、淘汰算法、穿透/雪崩与一致性。',
  MQ: '消息模型、投递语义、顺序、幂等与积压处理。',
  DISTRIBUTED: '一致性、分区、复制、选主与分布式事务。',
  LLM: '大模型结构、训练/推理、对齐与上下文窗口。',
  RAG: 'Embedding、切块、检索、重排与生成链路。',
  AGENT: 'Agent、工具调用、MCP、记忆与规划。',
  AI_ENG: 'AI 工程化：评测、可观测性、成本、安全与发布。',
}

export function domainDescription(domain: string): string {
  return DOMAIN_DESCRIPTIONS[domain] || '该方向的专业英文术语与缩写。'
}
