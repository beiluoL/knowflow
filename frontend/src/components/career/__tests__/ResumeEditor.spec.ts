// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ResumeEditor from '../ResumeEditor.vue';
import { defaultTheme } from '@/career/templates';
import type { CareerRecord } from '@/api/career/types';
import { getResumeDraft } from '@/api/career/draft';
vi.mock('@/api/career/draft', () => ({ getResumeDraft: vi.fn(), saveResumeDraft: vi.fn().mockResolvedValue({ revision: 1 }), deleteResumeDraft: vi.fn() }));
const record = (): CareerRecord => ({ id: 1, kind: 'RESUME', title: '我的简历', currentVersion: 1, version: 1, isDefault: false, confirmed: false, source: '负责开发\n交付项目', sections: { basics: [{ value: '负责开发\n交付项目', quote: '负责开发' }] } });
const create = (value = record()) => mount(ResumeEditor, { props: { record: value, suggestions: [] } });
const click = async (wrapper: ReturnType<typeof create>, label: string) => {
  const button = wrapper.findAll('button').find(item => item.text() === label);
  expect(button).toBeDefined();
  await button!.trigger('click');
};
describe('ResumeEditor 受控编辑', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getResumeDraft).mockResolvedValue({ revision: 0, baseVersion: 1, payload: null });
  });
  it('格式写入纯文本，保留证据，并可撤销重做', async () => {
    const wrapper = create();
    await flushPromises();
    await click(wrapper, '项目符号');
    expect(wrapper.get('textarea').element.value).toBe('• 负责开发\n• 交付项目');
    expect(wrapper.emitted('save')).toBeUndefined();
    await click(wrapper, '撤销');
    expect(wrapper.get('textarea').element.value).toBe('负责开发\n交付项目');
    await click(wrapper, '重做');
    await click(wrapper, '保存为新版本');
    const saved = wrapper.emitted('save')![0][0] as { sections: CareerRecord['sections'] };
    expect(saved.sections.basics[0]).toMatchObject({ value: '• 负责开发\n• 交付项目', quote: '负责开发' });
    wrapper.unmount();
    await flushPromises();
  });
  it('无效邮箱阻止正式保存，修正后可保存', async () => {
    const value = record();
    value.sections.basics[0].fields = { email: 'invalid' };
    const wrapper = create(value);
    await flushPromises();
    expect(wrapper.text()).toContain('请输入有效邮箱');
    expect(wrapper.findAll('button').find(item => item.text() === '保存为新版本')!.attributes('disabled')).toBeDefined();
    await wrapper.get('input[aria-label$="邮箱"]').setValue('person@example.com');
    await click(wrapper, '保存为新版本');
    expect(wrapper.emitted('save')).toHaveLength(1);
    wrapper.unmount();
    await flushPromises();
  });
  it('格式超过字数上限不截断内容', async () => {
    const value = record();
    value.sections.basics[0].value = '字'.repeat(500);
    const wrapper = create(value);
    await flushPromises();
    await click(wrapper, '项目符号');
    expect(wrapper.text()).toContain('格式调整后超过500字');
    expect(wrapper.get('textarea').element.value).toHaveLength(500);
    wrapper.unmount();
    await flushPromises();
  });
  it('实时预览使用传入主题且可以返回编辑', async () => {
    const wrapper = create();
    await wrapper.setProps({ previewTheme: defaultTheme('cn-minimal') });
    await click(wrapper, '实时 A4 预览');
    expect(wrapper.classes()).toContain('is-preview');
    expect(wrapper.findComponent({ name: 'CnMinimalTemplate' }).exists()).toBe(true);
    await click(wrapper, '返回内容编辑');
    expect(wrapper.classes()).not.toContain('is-preview');
    wrapper.unmount();
    await flushPromises();
  });
  it('内部拖拽改变正式保存顺序，外部拖放不改变条目', async () => {
    const value = record();
    value.sections.basics.push({ value: '交付项目', quote: '交付项目' });
    const wrapper = create(value);
    await flushPromises();
    await wrapper.findAll('.resume-claim')[1].trigger('drop');
    await wrapper.findAll('.resume-claim-index')[0].trigger('dragstart');
    await wrapper.findAll('.resume-claim')[1].trigger('drop');
    await click(wrapper, '保存为新版本');
    const saved = wrapper.emitted('save')![0][0] as { sections: CareerRecord['sections'] };
    expect(saved.sections.basics.map(item => item.value)).toEqual(['交付项目', '负责开发\n交付项目']);
    wrapper.unmount();
    await flushPromises();
  });
  it('解析锁定阻止程序触发的格式修改', async () => {
    const wrapper = create();
    await flushPromises();
    await wrapper.setProps({ parseBusy: true });
    await click(wrapper, '项目符号');
    expect(wrapper.get('textarea').element.value).toBe('负责开发\n交付项目');
    wrapper.unmount();
    await flushPromises();
  });
});
