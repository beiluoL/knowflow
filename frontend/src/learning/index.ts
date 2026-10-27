/**
 * Architecture-4：Learning Workflow 模块出口。
 * Workflow = Query（复用已有数据）+ Recommendation（规则 Resolver）+ Navigation（白名单路由）。
 *
 * Architecture-6：在 Workflow 之上叠加 Intelligence 层（纯函数，不新增表/API，§124-129）。
 *
 * Architecture-4.1：在 Event 之上叠加 Evidence 层（Evidence & Learning Strategy Engine）。
 */
export * from './types'
export * from './action-priority'
export * from './route'
export * from './intelligence'
export * from './coach'
export * from './evidence'
