// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
const register = vi.hoisted(() => vi.fn())
vi.mock('@/api', () => ({ authApi: { register } }))
import { useAuthStore } from '../auth'
import { useOnboardingStore } from '../onboarding'
beforeEach(() => { localStorage.clear(); setActivePinia(createPinia()); vi.resetAllMocks() })
describe('registration uses the real onboarding store', () => {
  it('marks only successfully registered accounts pending before their first shell visit', async () => {
    const auth = useAuthStore(); register.mockResolvedValueOnce({ token: 'test-token', user: { id: 91, username: 'new-user' } });
    await auth.register({ username: 'new-user', password: 'test-only-pass', email: 'test@example.invalid' });
    const guide = useOnboardingStore(); guide.syncUser(auth.user); expect(guide.open).toBe(true);
    register.mockRejectedValueOnce(new Error('conflict')); await expect(auth.register({ username: 'taken', password: 'test-only-pass', email: 'test@example.invalid' })).rejects.toThrow('conflict'); expect(auth.user?.id).toBe(91);
  })
})
