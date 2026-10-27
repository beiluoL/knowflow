// @vitest-environment happy-dom
import { describe,it,expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CategoryNode from '../CategoryNode.vue'
describe('knowledge category keyboard navigation',()=>{it('selects and expands the actual category with Enter',async()=>{const w=mount(CategoryNode,{props:{category:{id:1,name:'分类',children:[{id:2,name:'子分类'}]},level:0,expandedIds:new Set<number>(),activeCategoryId:null}});const row=w.find('.sidebar-item');expect(row.attributes('tabindex')).toBe('0');await row.trigger('keydown',{key:'Enter'});expect(w.emitted('select')?.[0][0]).toMatchObject({id:1});expect(w.emitted('toggle')?.[0][0]).toBe(1)})})
