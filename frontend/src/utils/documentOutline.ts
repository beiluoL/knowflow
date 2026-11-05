/** 读取净化后的渲染结果，避免围栏示例产生虚假标题。 */
export function documentOutline(html: string): {id: string; text: string; level: number}[] {
  const document = new DOMParser().parseFromString(html, 'text/html')
  return [...document.querySelectorAll<HTMLHeadingElement>('h2[id], h3[id]')].map(heading => ({
    id: heading.id,
    text: heading.textContent?.trim() || '',
    level: Number(heading.tagName.slice(1)),
  }))
}
