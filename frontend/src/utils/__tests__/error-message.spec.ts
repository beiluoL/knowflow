// @vitest-environment node
/**
 * Interaction OS（UI/UX 5.1）§六：统一错误文案契约测试
 *
 * 这一组测试的目的不是「覆盖率」，而是**锁死一条产品红线**：
 * 用户界面永远不许出现 "status code 502" / "ECONNABORTED" / "Network Error" 这类原始报错。
 */
import { describe, it, expect } from 'vitest'
import {
  classifyPageError,
  describePageError,
  extractStatus,
} from '@/utils/error-message'

/** 造一个近似 axios 错误的形状 */
function axiosError(status?: number, code?: string, message?: string) {
  return {
    ...(status != null ? { response: { status } } : {}),
    ...(code != null ? { code } : {}),
    ...(message != null ? { message } : {}),
  }
}

describe('extractStatus', () => {
  it('reads axios response.status', () => {
    expect(extractStatus(axiosError(502))).toBe(502)
  })

  it('reads a flat status field', () => {
    expect(extractStatus({ status: 403 })).toBe(403)
  })

  it('returns null when no numeric status', () => {
    expect(extractStatus(new Error('boom'))).toBeNull()
    expect(extractStatus(null)).toBeNull()
    expect(extractStatus(undefined)).toBeNull()
    expect(extractStatus({ status: 'nope' })).toBeNull()
  })
})

describe('classifyPageError —— 只认稳定特征，不模糊匹配', () => {
  it.each([
    [axiosError(400), 'badrequest'],
    [axiosError(401), 'auth'],
    [axiosError(403), 'forbidden'],
    [axiosError(404), 'notfound'],
    [axiosError(500), 'server'],
    [axiosError(502), 'server'],
    [axiosError(503), 'server'],
    [axiosError(undefined, 'ECONNABORTED'), 'timeout'],
    [axiosError(undefined, 'ETIMEDOUT'), 'timeout'],
    [axiosError(undefined, 'ERR_NETWORK'), 'offline'],
    [axiosError(undefined, undefined, 'Network Error'), 'offline'],
    [axiosError(undefined, undefined, 'Failed to fetch'), 'offline'],
    [axiosError(undefined, undefined, 'timeout of 10000ms exceeded'), 'timeout'],
  ])('classifies %o as %s', (err, expected) => {
    expect(classifyPageError(err)).toBe(expected)
  })

  it('status 优先于 code（502 + ECONNABORTED 判 server，不是 timeout）', () => {
    expect(classifyPageError(axiosError(502, 'ECONNABORTED'))).toBe('server')
  })

  it('无法分类时返回 unknown，而不是猜', () => {
    expect(classifyPageError(new Error('something weird'))).toBe('unknown')
    expect(classifyPageError('随便一个字符串')).toBe('unknown')
    expect(classifyPageError(null)).toBe('unknown')
  })

  it('不做模糊匹配：network down / err_network_down 不算 offline', () => {
    expect(classifyPageError(new Error('network down'))).toBe('unknown')
    expect(classifyPageError(axiosError(undefined, 'ERR_NETWORK_DOWN'))).toBe('unknown')
  })
})

describe('describePageError —— 三要素 + 绝不泄漏原始信息', () => {
  const RAW_MARKERS = ['502', 'status code', 'ECONNABORTED', 'Network Error', 'stack']

  it('502 场景：标题定位为服务不可用，且不含原始报错', () => {
    const d = describePageError(axiosError(502, undefined, 'Request failed with status code 502'), {
      subject: '无法加载闪卡',
    })
    expect(d.kind).toBe('server')
    expect(d.title).toBe('服务暂时不可用')
    expect(d.retryable).toBe(true)
    for (const m of RAW_MARKERS) {
      expect(d.title).not.toContain(m)
      expect(d.scope).not.toContain(m)
      expect(d.hint).not.toContain(m)
    }
  })

  it('原始信息只放进 rawForLog（供日志与断言，禁止渲染）', () => {
    const d = describePageError(axiosError(502, undefined, 'Request failed with status code 502'), {
      subject: 'X',
    })
    expect(d.rawForLog).toContain('502')
  })

  it('未分类错误降级为调用方 subject，而不是 "Error" / err.message', () => {
    const d = describePageError(new Error('TypeError: x is not a function'), {
      subject: '计划加载失败',
    })
    expect(d.kind).toBe('unknown')
    expect(d.title).toBe('计划加载失败')
    expect(d.title).not.toContain('TypeError')
  })

  it('scope=section 时明确「只影响这一块」', () => {
    const d = describePageError(axiosError(500), { subject: 'X', scope: 'section' })
    expect(d.scope).toContain('这一块')
  })

  it('scope 默认 page：整页不可用', () => {
    const d = describePageError(axiosError(500), { subject: 'X' })
    expect(d.scope).toContain('这个页面')
  })

  it('401/403/404/400 不值得重试（retryable=false），5xx 与网络类值得', () => {
    expect(describePageError(axiosError(400), { subject: 'X' }).retryable).toBe(false)
    expect(describePageError(axiosError(401), { subject: 'X' }).retryable).toBe(false)
    expect(describePageError(axiosError(403), { subject: 'X' }).retryable).toBe(false)
    expect(describePageError(axiosError(404), { subject: 'X' }).retryable).toBe(false)
    expect(describePageError(axiosError(500), { subject: 'X' }).retryable).toBe(true)
    expect(describePageError(axiosError(undefined, 'ERR_NETWORK'), { subject: 'X' }).retryable).toBe(true)
  })

  // P1-D §九：400 必须与 5xx 明确区分 —— 「你发来的请求有问题」不是「服务挂了」，
  // 否则用户会被引导去做无意义的「稍后重试」。
  it('400 定位为「请求参数不正确」，绝不与 server / 网络类混淆', () => {
    const d = describePageError(axiosError(400, undefined, 'Request failed with status code 400'), {
      subject: '提交失败',
    })
    expect(d.kind).toBe('badrequest')
    expect(d.title).toBe('请求参数不正确')
    expect(d.kind).not.toBe('server')
    expect(d.kind).not.toBe('offline')
    expect(d.retryable).toBe(false)
    for (const m of RAW_MARKERS) {
      expect(d.title).not.toContain(m)
      expect(d.hint).not.toContain(m)
    }
  })

  it('六类状态码互不混同（401/403/404/400/500/网络各归其类）', () => {
    expect(classifyPageError(axiosError(401))).toBe('auth')
    expect(classifyPageError(axiosError(403))).toBe('forbidden')
    expect(classifyPageError(axiosError(404))).toBe('notfound')
    expect(classifyPageError(axiosError(400))).toBe('badrequest')
    expect(classifyPageError(axiosError(500))).toBe('server')
    expect(classifyPageError(axiosError(undefined, 'ERR_NETWORK'))).toBe('offline')
    // 只有 5xx 才是「服务暂时不可用」
    const titles = [400, 401, 403, 404].map(
      (s) => describePageError(axiosError(s), { subject: 'X' }).title,
    )
    expect(titles).not.toContain('服务暂时不可用')
  })

  it('三要素永远非空 —— 不允许出现「没有解释的报错」', () => {
    const cases: unknown[] = [axiosError(502), new Error('x'), 'str', null, undefined, 42]
    for (const c of cases) {
      const d = describePageError(c, { subject: '出问题了' })
      expect(d.title.length).toBeGreaterThan(0)
      expect(d.scope.length).toBeGreaterThan(0)
      expect(d.hint.length).toBeGreaterThan(0)
    }
  })
})
