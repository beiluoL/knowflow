// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
const api=vi.hoisted(()=>({listDrawings:vi.fn(),createDrawing:vi.fn(),updateDrawing:vi.fn(),getDrawing:vi.fn(),deleteDrawing:vi.fn()}))
vi.mock('@/api/drawing',()=>api)
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()}),onBeforeRouteLeave:vi.fn()}))
vi.mock('@/utils/toast',()=>({notify:vi.fn()}))
import DrawingEditor from '../DrawingEditor.vue'
beforeEach(()=>{vi.clearAllMocks();api.listDrawings.mockResolvedValue([])})
describe('real drawing module',()=>{
 it('imports actual Vue Flow modules and mounts the empty page',async()=>{const w=mount(DrawingEditor,{global:{stubs:{Icon:true,MiniAppShell:{template:'<div><slot/></div>'}}}});await flushPromises();expect(w.find('.dw-main').exists()).toBe(true);expect(w.find('.dw-blank h2').text()).toBe('流程图');w.unmount()})
 it('provides retry for a failed drawing library',async()=>{api.listDrawings.mockRejectedValueOnce(new Error('offline'));const w=mount(DrawingEditor,{global:{stubs:{Icon:true,MiniAppShell:{template:'<div><slot/></div>'}}}});await flushPromises();expect(w.find('[role=alert]').exists()).toBe(true);await w.find('[role=alert] button').trigger('click');await flushPromises();expect(api.listDrawings).toHaveBeenCalledTimes(2);w.unmount()})
})
