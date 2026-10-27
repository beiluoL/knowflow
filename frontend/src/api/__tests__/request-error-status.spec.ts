// @vitest-environment happy-dom
/**
 * P1-D §九：拦截器必须把「可分类的状态码」留给错误描述层。
 *
 * ── 为什么需要这条测试 ──────────────────────────────────────────────────
 * 修复前拦截器用裸 `new Error(message)` 拒绝：
 *   - HTTP 403 + `{"message":"无权操作该任务"}`        → 状态码被丢掉
 *   - HTTP 200 + `{"code":500,"message":"系统内部错误"}` → 状态码被丢掉
 * 于是 `classifyPageError()` 只能判成 `unknown`，页面把「没有权限」显示成
 * 「未知错误，稍后重试」—— 既误导用户，又让人误以为是服务端挂了。
 *
 * 这里用自定义 adapter 在**真实 axios 实例**上模拟后端响应，
 * 断言「文案行为不变 + 状态码可分类」两件事同时成立。
 */
import { describe, it, expect, afterEach } from 'vitest'
import type { AxiosRequestConfig } from 'axios'
import request, { apiGet } from '@/api/request'
import { classifyPageError, describePageError } from '@/utils/error-message'

type Adapter = typeof request.defaults.adapter
const originalAdapter = request.defaults.adapter

/** 模拟「HTTP 200 + Result 信封」的成功响应 */
function respondWith(body: unknown) {
  request.defaults.adapter = (async (config: AxiosRequestConfig) => ({
    data: body,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })) as unknown as Adapter
}

/** 模拟「HTTP 错误 + Result 信封」的失败响应 */
function rejectWith(status: number, body: unknown) {
  request.defaults.adapter = (async (config: AxiosRequestConfig) => {
    const err = new Error(`Request failed with status code ${status}`) as Error & {
      config?: unknown
      response?: unknown
      isAxiosError?: boolean
    }
    err.config = config
    err.isAxiosError = true
    err.response = { status, statusText: '', data: body, headers: {}, config }
    throw err
  }) as unknown as Adapter
}

afterEach(() => {
  request.defaults.adapter = originalAdapter
})

describe('P1-D 拦截器 —— 状态码不再丢失', () => {
  it('HTTP 200 + code 200 → 正常解包 data（行为不变）', async () => {
    respondWith({ code: 200, message: 'ok', data: { hello: 'world' } })
    await expect(apiGet('/ping')).resolves.toEqual({ hello: 'world' })
  })

  it('HTTP 403 业务错误 → 仍透传后端文案，但可被归类为 forbidden', async () => {
    rejectWith(403, { code: 403, message: '无权操作该任务' })

    const err = await request.get('/learning/tasks/1').catch((e: unknown) => e)

    expect((err as Error).message).toBe('无权操作该任务')
    expect(classifyPageError(err)).toBe('forbidden')
    expect(describePageError(err, { subject: '操作任务' }).title).toBe('没有访问权限')
    expect(describePageError(err, { subject: '操作任务' }).retryable).toBe(false)
  })

  it('HTTP 500 → 归类为 server（而不是 unknown）', async () => {
    rejectWith(500, { code: 500, message: '系统内部错误' })

    const err = await request.get('/learning/stats/mastery').catch((e: unknown) => e)

    expect(classifyPageError(err)).toBe('server')
    expect(describePageError(err, { subject: '掌握分布加载失败' }).title).toBe('服务暂时不可用')
  })

  it('HTTP 200 + 业务 code 500 → 也归类为 server（业务故障不再伪装成 unknown）', async () => {
    respondWith({ code: 500, message: '系统内部错误' })

    const err = await request.get('/x').catch((e: unknown) => e)

    expect((err as Error).message).toBe('系统内部错误')
    expect(classifyPageError(err)).toBe('server')
  })
})
