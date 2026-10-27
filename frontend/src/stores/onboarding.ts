import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserVO } from '@/api/types'

const key = (id: number) => `knowflow:welcome:v1:${id}`
export const useOnboardingStore = defineStore('onboarding', () => {
  const open = ref(false)
  const userId = ref<number | null>(null)
  const completed = new Set<number>()
  const pending = new Set<number>()
  function read(id: number) { try { return localStorage.getItem(key(id)) } catch { return null } }
  function write(id: number, value: string) { try { localStorage.setItem(key(id), value) } catch { /* UI preference; session memory remains available. */ } }
  function markNewUser(id: number) { pending.add(id); write(id, 'pending') }
  function syncUser(user: Pick<UserVO, 'id' | 'role' | 'createTime'> | null) {
    const switched = userId.value !== (user?.id ?? null)
    userId.value = user?.id ?? null
    if (switched || !user || user.role === 'ADMIN') open.value = false
    if (!user || user.role === 'ADMIN' || completed.has(user.id) || read(user.id) === 'done') return
    const created = user.createTime ? Date.parse(user.createTime.replace(' ', 'T')) : NaN
    const age = Date.now() - created
    if (pending.has(user.id) || read(user.id) === 'pending' || (age >= 0 && age < 7 * 24 * 60 * 60 * 1000)) open.value = true
  }
  function dismiss() { if (userId.value != null) { completed.add(userId.value); pending.delete(userId.value); write(userId.value, 'done') }; open.value = false }
  function reopen() { if (userId.value != null) open.value = true }
  return { open, userId, markNewUser, syncUser, dismiss, reopen }
})
