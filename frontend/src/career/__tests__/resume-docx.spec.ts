// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { Packer } from 'docx';
import JSZip from 'jszip';
import { buildResumeDocx } from '../resume-docx';
import { defaultTheme, RESUME_TEMPLATES } from '../templates';
import { hasResumeDocumentContent, toDocument, type ResumeDocumentViewModel } from '../resume-document';
import type { CareerRecord } from '@/api/career/types';

const doc: ResumeDocumentViewModel = {
  title: '中文简历', name: '张三', headline: 'Java 工程师', email: 'zhang@example.com', phone: '13800138000', city: '上海', version: 3, confirmed: true,
  sections: ['education', 'experience', 'projects', 'skills', 'certificates', 'selfIntro', 'custom'].map((key) => ({
    key, label: `章节-${key}`, chips: key === 'skills' ? ['Java', '中文技能'] : [],
    items: key === 'skills' ? [] : [{ title: `标题-${key}`, subtitle: '角色', period: '2024–2026', bullets: ['中文成果 <&>', '第二条'], raw: '原始内容' }],
  })),
};
describe('native editable resume OOXML', () => {
  it.each(RESUME_TEMPLATES.map((template) => template.id))('%s preserves semantic content and configured ordering', async (id) => {
    const theme = defaultTheme(id);
    theme.sectionOrder = [...theme.sectionOrder].reverse();
    const zip = await JSZip.loadAsync(await Packer.toBuffer(buildResumeDocx(doc, theme)));
    expect(zip.file('[Content_Types].xml')).not.toBeNull();
    const xml = await zip.file('word/document.xml')!.async('string');
    const parsed = new DOMParser().parseFromString(xml, 'application/xml');
    const text = [...parsed.getElementsByTagName('w:t')].map((node) => node.textContent).join('|');
    for (const value of ['张三', 'zhang@example.com', '13800138000', '上海', '中文成果 <&>', '中文技能', '章节-custom']) expect(text).toContain(value);
    expect(text.indexOf('章节-custom')).toBeLessThan(text.indexOf('章节-education'));
    expect(xml).toContain('w:pgSz');
    expect(xml).not.toContain('<w:drawing');
    expect(xml).toContain('w:keepNext');
    expect(await zip.file('word/styles.xml')!.async('string')).toContain(theme.fontFamily === 'serif' ? 'SimSun' : 'Microsoft YaHei');
  });
  it('retains source contacts when only projects have been structured', async () => {
    const partial: CareerRecord = { id: 9, kind: 'RESUME', title: '部分整理', version: 1, currentVersion: 1, isDefault: false, confirmed: true,
      source: '姓名：测试工程师\n邮箱：source@example.invalid\n电话：13800000000\nJava项目',
      sections: { projects: [{ value: 'Java项目', quote: 'Java项目' }] } };
    const model = toDocument(partial);
    expect(model.email).toBe('source@example.invalid'); expect(model.phone).toBe('13800000000');
    const zip = await JSZip.loadAsync(await Packer.toBuffer(buildResumeDocx(model, defaultTheme('ats-classic'))));
    const xml = await zip.file('word/document.xml')!.async('string');
    expect(xml).toContain('source@example.invalid'); expect(xml).toContain('Java项目');
    const relationships = await zip.file('word/_rels/document.xml.rels')!.async('string');
    expect(relationships).toContain('mailto:source@example.invalid');
  });
  it('preserves structured fields through the shared HTML/PDF/DOCX model', async () => {
    const record: CareerRecord = { id: 1, kind: 'RESUME', currentVersion: 1, isDefault: false, source: '项目说明 社区贡献', title: '测试', version: 1, confirmed: false, sections: {
      projects: [{ value: '项目说明', quote: '', fields: { project: '知识库', role: '开发', techStack: 'Java / Vue', outcome: '降低延迟 30%' } }],
      custom: [{ value: '社区贡献', quote: '', fields: { heading: '开源贡献' } }],
    } };
    const viewModel = toDocument(record);
    const zip = await JSZip.loadAsync(await Packer.toBuffer(buildResumeDocx(viewModel, defaultTheme('ats-classic'))));
    const xml = await zip.file('word/document.xml')!.async('string');
    for (const text of ['知识库', '开发', 'Java / Vue', '降低延迟 30%', '开源贡献', '社区贡献']) expect(xml).toContain(text);
  });
  it('exports explicit contacts with basics hidden and keeps company and skill attributes', async () => {
    const record: CareerRecord = {
      id: 2, kind: 'RESUME', currentVersion: 1, isDefault: false, source: '', title: '个人简历', version: 1, confirmed: true,
      sections: {
        basics: [{ value: '旧姓名 old@example.com 13800138000 北京', quote: '', fields: {
          name: '李明', email: 'li@example.org', phone: '+1 202-555-0100', city: '多伦多',
        } }],
        experience: [{ value: '负责平台研发', quote: '', fields: { company: '示例公司', role: '后端工程师' } }],
        skills: [{ value: 'Java', quote: '', fields: { group: '编程语言', proficiency: '熟练' } }],
      },
    };
    const viewModel = toDocument(record);
    expect(viewModel).toMatchObject({ name: '李明', email: 'li@example.org', phone: '+1 202-555-0100', city: '多伦多' });
    expect(viewModel.sections.find((section) => section.key === 'experience')!.items[0].title).toBe('示例公司');
    expect(viewModel.sections.find((section) => section.key === 'skills')!.chips).toEqual(['编程语言 · Java · 熟练']);
    const theme = defaultTheme('ats-classic');
    theme.visibleSections = ['experience', 'skills'];
    const zip = await JSZip.loadAsync(await Packer.toBuffer(buildResumeDocx(viewModel, theme)));
    const xml = await zip.file('word/document.xml')!.async('string');
    const parsed = new DOMParser().parseFromString(xml, 'application/xml');
    const text = [...parsed.getElementsByTagName('w:t')].map((node) => node.textContent).join('|');
    for (const value of ['李明', 'li@example.org', '+1 202-555-0100', '多伦多', '示例公司', '编程语言', '熟练', 'Java']) expect(text).toContain(value);
    expect(text).not.toContain('old@example.com');
    expect(text).not.toContain('基本信息');
  });
  it('exports the exact unstructured source lines without creating claims', async () => {
    const source = '姓名：王明\nwang@example.com\n\n工作经历：\n  示例公司 2020—2026\n· 保留原文和空行 <&>';
    const record: CareerRecord = { id: 3, kind: 'RESUME', currentVersion: 1, isDefault: false, source, title: '原始简历', version: 1, confirmed: false, sections: {} };
    const model = toDocument(record);
    expect(model.sections[0].label).toBe('简历正文');
    expect(model.sections[0].items.map((item) => item.raw).join('\n')).toBe(source);
    expect(record.sections).toEqual({});
    const theme = defaultTheme('ats-classic');
    const zip = await JSZip.loadAsync(await Packer.toBuffer(buildResumeDocx(model, theme)));
    const parsed = new DOMParser().parseFromString(await zip.file('word/document.xml')!.async('string'), 'application/xml');
    const text = [...parsed.getElementsByTagName('w:t')].map((node) => node.textContent).join('\n');
    for (const line of source.split('\n').filter(Boolean)) expect(text).toContain(line);
    expect(hasResumeDocumentContent(model, theme)).toBe(true);
  });
  it.each(['', '  \n ', '[原文件未提取到可用正文，请手动粘贴简历文本后再确认]'])('rejects empty or unavailable source %s', (source) => {
    const record: CareerRecord = { id: 4, kind: 'RESUME', currentVersion: 1, isDefault: false, source, title: '空简历', version: 1, confirmed: false, sections: {} };
    const model = toDocument(record);
    const theme = defaultTheme('ats-classic');
    expect(model.sections).toEqual([]);
    expect(hasResumeDocumentContent(model, theme)).toBe(false);
    expect(() => buildResumeDocx(model, theme)).toThrow('先补充正文');
  });
  it('applies Letter size, margins, visibility and long editable text', async () => {
    const theme = defaultTheme('ats-classic');
    theme.pageSize = 'Letter';
    theme.visibleSections = ['experience'];
    const longDoc = structuredClone(doc);
    longDoc.sections.find((section) => section.key === 'experience')!.items[0].bullets.push('长简历内容'.repeat(3000));
    theme.margin.left = 25.4;
    const zip = await JSZip.loadAsync(await Packer.toBuffer(buildResumeDocx(longDoc, theme)));
    const xml = await zip.file('word/document.xml')!.async('string');
    expect(xml).toContain('w:w="12240"');
    expect(xml).toContain('w:h="15840"');
    expect(xml).toContain('w:left="1440"');
    expect(xml).not.toContain('章节-education');
    expect(xml).toContain('章节-experience');
    expect(xml).toContain('长简历内容'.repeat(3000));
  });
});
