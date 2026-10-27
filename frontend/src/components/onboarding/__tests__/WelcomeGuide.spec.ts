// @vitest-environment happy-dom
import { describe,it,expect,beforeEach } from 'vitest'
import { createPinia,setActivePinia } from 'pinia'
import { mount,flushPromises } from '@vue/test-utils'
import WelcomeGuide from '../WelcomeGuide.vue'
import { useOnboardingStore } from '@/stores/onboarding'
describe('welcome guide navigation and dismissal',()=>{
  it('returns focus to the stable avatar when the menu item was removed', async () => {
    const avatar = document.createElement('button'); avatar.dataset.test = 'onboarding-trigger'; document.body.appendChild(avatar);
    const guide = useOnboardingStore(); guide.syncUser({ id: 5 }); guide.reopen(); const w = mount(WelcomeGuide, { attachTo: document.body, global: { stubs: { Teleport: true } } }); await flushPromises();
    await w.find('[role="dialog"]').trigger('keydown', { key: 'Escape' }); await flushPromises(); expect(document.activeElement).toBe(avatar); w.unmount(); avatar.remove();
  })
  it('resets progress on an account switch while open, and restores original body scroll', async () => {
    document.body.style.overflow = 'auto'; const guide = useOnboardingStore(); guide.markNewUser(1); guide.syncUser({ id: 1 });
    const w = mount(WelcomeGuide, { global: { stubs: { Teleport: true } } }); await flushPromises(); await w.findAll('button').find(b => b.text() === '下一步')!.trigger('click');
    guide.markNewUser(2); guide.syncUser({ id: 2 }); await flushPromises(); expect(w.text()).toContain('1 / 5'); guide.dismiss(); await flushPromises(); expect(document.body.style.overflow).toBe('auto'); w.unmount(); document.body.style.overflow = '';
  })
  beforeEach(()=>{localStorage.clear();setActivePinia(createPinia())})
  it('completes five steps and emits a start action once',async()=>{
    const guide=useOnboardingStore();guide.markNewUser(1);guide.syncUser({id:1});const w=mount(WelcomeGuide,{global:{stubs:{Teleport:true}}});await flushPromises();expect(w.find('[role="dialog"]').exists()).toBe(true);
    for(let i=0;i<4;i++){const next=w.findAll('button').find(b=>b.text()==='下一步')!;await next.trigger('click')};await w.findAll('button').find(b=>b.text()==='开始收集')!.trigger('click');expect(guide.open).toBe(false);expect(w.emitted('start')).toHaveLength(1);w.unmount();
  })
  it('Escape skips, restores scroll, and manual reopen restarts',async()=>{
    const guide=useOnboardingStore();guide.syncUser({id:3});guide.reopen();const w=mount(WelcomeGuide,{global:{stubs:{Teleport:true}}});await flushPromises();await w.find('[role="dialog"]').trigger('keydown',{key:'Escape'});expect(guide.open).toBe(false);await flushPromises();expect(document.body.style.overflow).not.toBe('hidden');guide.reopen();await flushPromises();expect(w.text()).toContain('1 / 5');w.unmount();
  })
})
