/**
 * Architecture-4.1：Evidence & Learning Strategy Engine 模块出口。
 *
 * 数据流（§30）：
 *   User Action → App → LearningEvent → Evidence Resolver → Evidence
 *     → Evidence Aggregation → Diagnosis(+RootCause) → Strategy
 *     → Action Candidate(+App Availability) → 既有 NextAction Resolver → Real App
 *
 * 本模块全部为纯函数：不调 API、不持有状态、不写库（§32-35），
 * 因此与既有 Event / Mastery / Review / NextAction 零冲突、可独立测试。
 */
export * from './types'
export * from './resolver'
export * from './aggregate'
export * from './first-principles'
export * from './diagnosis'
export * from './strategy'
export * from './action-candidate'
