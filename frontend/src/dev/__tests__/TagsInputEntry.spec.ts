// @vitest-environment happy-dom
import {describe,it,expect} from 'vitest'
import {mount} from '@vue/test-utils'
import {entries} from '../registry'
import {stories,storyDefaults} from '../stories'
import {createExample} from '../code-examples'
import ComponentPreview from '../ComponentPreview.vue'

const entry=entries.find(e=>e.name==='TagsInput')!

describe('TagsInput playground entry',()=>{
 it('is a unique real production entry reusing the public component',()=>{
  expect(entries.filter(e=>e.name==='TagsInput')).toHaveLength(1)
  expect(entry.implementationStatus).toBe('已接入真实组件')
  expect(entry.sourcePath).toBe('frontend/src/components/ui/KfTagsInput.vue')
  expect(entry.category).toBe('表单输入')
  expect(entry.events).toContain('add')
  expect(entry.events).toContain('remove')
  expect(entry.slots).toEqual(['tag','prefix','suffix'])
  expect(entry.production.status).toBe('PRODUCTION')
 })

 it('registers the real props and story coverage',()=>{
  const names=entry.props.map(p=>p.name)
  for(const name of ['modelValue','placeholder','disabled','readonly','maxTags','maxTagLength','allowDuplicates','size','error','clearable'])expect(names).toContain(name)
  const own=stories.filter(s=>s.componentId==='TagsInput').map(s=>s.storyId)
  expect(own).toEqual(expect.arrayContaining(['basic','multiple-tags','empty','long-tags','many-tags','max-tags','ime','disabled','readonly','error']))
 })

 it('renders the real KfTagsInput in the preview',()=>{
  const story=stories.find(s=>s.componentId==='TagsInput'&&s.storyId==='multiple-tags')!
  const w=mount(ComponentPreview,{props:{entry,settings:storyDefaults(entry,story),size:'md',variant:'primary',disabled:false,loading:false,icon:false,rightIcon:false,error:false,label:'标签'}})
  expect(w.findAll('.kf-tags-input')).toHaveLength(1)
  expect(w.findAll('.kf-tags-input__chip')).toHaveLength(6)
  w.unmount()
 })

 it('generates a compilable example with an array v-model',()=>{
  const story=stories.find(s=>s.componentId==='TagsInput'&&s.storyId==='basic')!
  const code=createExample(entry,storyDefaults(entry,story))
  expect(code).toContain("import KfTagsInput from '@/components/ui/KfTagsInput.vue'")
  expect(code).toContain('const value=ref(["Vue","TypeScript"])')
  expect(code).toContain('v-model="value"')
 })

 it('keeps the inspector target aligned with the single-surface focus contract',()=>{
  expect(entry.designSpecs).toMatchObject({minHeight:44,padding:'8px 12px',borderWidth:0,borderStyle:'none',outlineWidth:0,outlineStyle:'none',boxShadow:'none',backgroundColor:'var(--kb-field-surface)'})
 })
})
