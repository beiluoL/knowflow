/** Native editable OOXML; shared semantic model with HTML/PDF. docx: MIT. */
import { BorderStyle, Document, ExternalHyperlink, HeadingLevel, Packer, Paragraph, TextRun } from 'docx';
import type { ResumeTheme } from '@/api/career/types';
import { hasResumeDocumentContent, orderSections, type ResumeDocumentViewModel } from './resume-document';

const TWIPS_PER_MM = 1440 / 25.4;
const FONTS = { sans: 'Microsoft YaHei', serif: 'SimSun', mono: 'Consolas' };
export function buildResumeDocx(doc: ResumeDocumentViewModel, theme: ResumeTheme): Document {
  if (!hasResumeDocumentContent(doc, theme)) throw new Error('简历没有可导出正文，请先补充正文并选择可见章节');
  const accent = theme.accentColor.replace('#', '');
  const children: Paragraph[] = [new Paragraph({
    heading: HeadingLevel.TITLE,
    children: [new TextRun({ text: doc.name, bold: true, size: theme.fontSize * 3.8, color: accent })],
  })];
  if (doc.headline) children.push(new Paragraph({ text: doc.headline }));
  const contacts = [doc.phone, doc.email, doc.city].filter(Boolean);
  if (contacts.length) children.push(new Paragraph({ children: contacts.flatMap((text, index) => [
    ...(index ? [new TextRun(' · ')] : []),
    ...(text === doc.email || text === doc.phone ? [new ExternalHyperlink({ link: `${text === doc.email ? 'mailto' : 'tel'}:${text}`, children: [new TextRun({ text, style: 'Hyperlink' })] })] : [new TextRun(text)]),
  ]) }));
  for (const section of orderSections(doc, theme.sectionOrder, theme.visibleSections)) {
    children.push(new Paragraph({
      heading: HeadingLevel.HEADING_1,
      keepNext: true,
      spacing: { before: theme.sectionGap * 15, after: 90 },
      border: theme.templateId === 'cn-minimal' ? undefined : {
        bottom: { color: accent, style: BorderStyle.SINGLE, size: 4 },
      },
      children: [new TextRun({ text: section.label, bold: true, color: accent })],
    }));
    if (section.chips.length) children.push(new Paragraph({ text: section.chips.join(' · ') }));
    for (const item of section.items) {
      const title = [item.title, item.subtitle, item.period].filter(Boolean).join(' · ');
      if (title) children.push(new Paragraph({ keepNext: true, children: [new TextRun({ text: title, bold: true })] }));
      for (const line of item.bullets) {
        children.push(new Paragraph({ text: line, ...(item.bullets.length > 1 ? { bullet: { level: 0 } } : {}), widowControl: true }));
      }
    }
  }
  const letter = theme.pageSize === 'Letter';
  return new Document({
    title: doc.title,
    creator: 'KnowFlow',
    description: `Resume ${theme.templateId} · version ${doc.version}`,
    styles: { default: { document: {
      run: { font: { name: FONTS[theme.fontFamily], eastAsia: FONTS[theme.fontFamily] }, size: theme.fontSize * 2 },
      paragraph: { spacing: { line: Math.round(theme.lineHeight * 240), after: 80 } },
    } } },
    sections: [{ properties: { page: {
      size: { width: Math.round((letter ? 215.9 : 210) * TWIPS_PER_MM), height: Math.round((letter ? 279.4 : 297) * TWIPS_PER_MM) },
      margin: Object.fromEntries(Object.entries(theme.margin).map(([side, mm]) => [side, Math.round(mm * TWIPS_PER_MM)])),
    } }, children }],
  });
}
export async function downloadResumeDocx(doc: ResumeDocumentViewModel, theme: ResumeTheme): Promise<void> {
  const blob = await Packer.toBlob(buildResumeDocx(doc, theme));
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${doc.title.replace(/[\\/:*?"<>|]/g, '_') || 'resume'}.docx`;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
