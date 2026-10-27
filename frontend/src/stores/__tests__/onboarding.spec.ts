// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useOnboardingStore } from '../onboarding'
describe('account-scoped welcome guide',()=>{
  it('closes automatic welcome if the current identity changes to admin', () => {
    const guide = useOnboardingStore(); guide.markNewUser(40); guide.syncUser({ id: 40 }); expect(guide.open).toBe(true); guide.syncUser({ id: 40, role: 'ADMIN' }); expect(guide.open).toBe(false);
  })
  beforeEach(()=>{localStorage.clear();setActivePinia(createPinia())})
  it('register opens once and skip survives reload',()=>{
    let guide=useOnboardingStore();guide.markNewUser(21);guide.syncUser({id:21});expect(guide.open).toBe(true);guide.dismiss();
    setActivePinia(createPinia());guide=useOnboardingStore();guide.syncUser({id:21});expect(guide.open).toBe(false);guide.reopen();expect(guide.open).toBe(true);
  })
  it('never reuses another account completion',()=>{
    const guide=useOnboardingStore();guide.markNewUser(21);guide.syncUser({id:21});guide.dismiss();guide.markNewUser(22);guide.syncUser({id:22});expect(guide.open).toBe(true);guide.syncUser(null);expect(guide.open).toBe(false);
  })
  it('old and admin users are not interrupted; recent new user is eligible',()=>{
    const guide=useOnboardingStore();guide.syncUser({id:11,createTime:'2020-01-01'});expect(guide.open).toBe(false);
    guide.syncUser({id:12,createTime:new Date().toISOString(),role:'ADMIN'});expect(guide.open).toBe(false);
    guide.syncUser({id:13,createTime:new Date().toISOString()});expect(guide.open).toBe(true);
  })
  it('unavailable persistence does not crash or repeat within the session',()=>{
    const spy=vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('quota')});
    const guide=useOnboardingStore();guide.markNewUser(31);guide.syncUser({id:31});guide.dismiss();guide.syncUser({id:31});expect(guide.open).toBe(false);spy.mockRestore();
  })
})
