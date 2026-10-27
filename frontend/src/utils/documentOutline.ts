/** Read the sanitized renderer output so fenced examples never become phantom headings. */
export function documentOutline(html: string): {id: string; text: string; level: number}[] {
  const document = new DOMParser().parseFromString(html, 'text/html')
  return [...document.querySelectorAll<HTMLHeadingElement>('h2[id], h3[id]')].map(heading => ({
    id: heading.id,
    text: heading.textContent?.trim() || '',
    level: Number(heading.tagName.slice(1)),
  }))
}
