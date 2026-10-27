// @vitest-environment happy-dom
import { beforeEach, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
vi.mock('vue-router',()=>({useRoute:()=>({meta:{},path:'/admin/users',fullPath:'/admin/users'})}))
vi.mock('@/stores/background',()=>({useBackgroundStore:()=>({isActive:false})}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>({isAdmin:true,user:{role:'ADMIN'}})}))
import AppShell from '../AppShell.vue'
beforeEach(()=>{Object.defineProperty(window,'innerWidth',{value:390,writable:true,configurable:true})})
it('关闭抽屉不可Tab进入；打开后Escape关闭并恢复触发焦点',async()=>{const w=mount(AppShell,{attachTo:document.body,global:{stubs:{Icon:true,RouterLink:{template:'<a href="/admin/users"><slot/></a>'},BTopbar:{template:'<button @click="$emit(\'toggle-sidebar\')">菜单</button>'}}}});await flushPromises();expect(w.get('[data-test="admin-sidebar"]').attributes('inert')).toBeDefined();const trigger=w.findAll('button').find(b=>b.text()==='菜单')!;(trigger.element as HTMLElement).focus();await trigger.trigger('click');await flushPromises();expect(w.get('[data-test="admin-sidebar"]').attributes('inert')).toBeUndefined();expect(w.get('[data-test="admin-content"]').attributes('inert')).toBeDefined();document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await flushPromises();expect(w.get('[data-test="admin-sidebar"]').attributes('inert')).toBeDefined();expect(document.activeElement).toBe(trigger.element);w.unmount()})
