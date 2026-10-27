// @vitest-environment happy-dom
import { describe,it,expect } from 'vitest'
import { renderMarkdown } from '../markdown'
describe('stored runnable code is display-only',()=>{
  it.each(['js-run','python-run','java-run'])('preserves literal %s code and a safe copy payload',language=>{
    const code='</textarea><img src="invalid" onerror="void 0">\nconsole.log("sample")'
    const root=document.createElement('div')
    root.innerHTML=renderMarkdown(`\`\`\`${language}\n${code}\n\`\`\``)
    expect(root.querySelector('code')?.textContent).toBe(code+'\n')
    expect(root.querySelector('img')).toBeNull()
    expect(root.querySelector('.interactive-code')).toBeNull()
    expect(decodeURIComponent(root.querySelector('button')!.getAttribute('data-code')!)).toBe(code+'\n')
  })
  it('retains the separate declarative quiz format',()=>{
    const root=document.createElement('div');root.innerHTML=renderMarkdown('```quiz-run\nQ: test\nA. yes\n答案: A\n```')
    expect(root.querySelector('.interactive-quiz')?.getAttribute('data-quiz')).toContain('Q: test')
  })
})
