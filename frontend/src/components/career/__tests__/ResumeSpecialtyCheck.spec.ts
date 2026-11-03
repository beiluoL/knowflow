// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ResumeSpecialtyCheck from '../ResumeSpecialtyCheck.vue';
import { listRecords } from '@/api/career/records';
import { checkStudioResume, listStudioSkills } from '@/api/career/studio';

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/api/career/records', () => ({ listRecords: vi.fn() }));
vi.mock('@/api/career/studio', () => ({ listStudioSkills: vi.fn(), checkStudioResume: vi.fn() }));
vi.mock('@/utils/toast', () => ({ getApiError: () => '连接失败' }));

const create = () => mount(ResumeSpecialtyCheck, { global: { stubs: {
  Select: true, StateView: true,
  ErrorState: { props: ['description'], template: '<div>{{ description }}<button @click="$emit(\'retry\')">重试</button></div>' },
  EmptyState: { props: ['title'], template: '<div>{{ title }}<slot name="action" /></div>' },
  Button: { template: '<button><slot /></button>' },
} } });

describe('专项体检', () => {
  beforeEach(() => { vi.clearAllMocks(); });
  it('空简历不生成结果或调用检查', async () => {
    vi.mocked(listRecords).mockResolvedValue([]);
    vi.mocked(listStudioSkills).mockResolvedValue([]);
    const wrapper = create();
    await flushPromises();
    expect(wrapper.text()).toContain('暂无可检查内容');
    expect(checkStudioResume).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it('注册表加载失败显式报错并支持重试', async () => {
    vi.mocked(listRecords).mockResolvedValue([]);
    vi.mocked(listStudioSkills).mockRejectedValueOnce(new Error('network')).mockResolvedValue([]);
    const wrapper = create();
    await flushPromises();
    expect(wrapper.text()).toContain('连接失败');
    await wrapper.find('button').trigger('click');
    await flushPromises();
    expect(listStudioSkills).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('暂无可检查内容');
    wrapper.unmount();
  });
});
