import {describe,it,expect} from 'vitest'
import {entries} from '../registry'
describe('V6 production catalog',()=>{
 it('separates machine implementation status from missing quality evidence',()=>{for(const entry of entries){expect(entry).toHaveProperty('production');expect((entry as any).production.status).toBe(entry.implementationStatus==='已接入真实组件'?'PRODUCTION':entry.implementationStatus==='仅有内部演示'?'DEMO_ONLY':'PLANNED');expect((entry as any).production.quality.Types.status).toBe('NOT VERIFIED')}})
 it('promotes four tested controls using real independent sources',()=>{for(const name of ['Radio','Switch','Tabs','SearchInput']){const entry=entries.find(e=>e.name===name)!;expect(entry.implementationStatus).toBe('已接入真实组件');expect(entry.component).toBeTruthy();expect(entry.sourcePath).toBe(`frontend/src/components/ui/${name}.vue`)}})
})
it('distinguishes source defaults and required API from illustrative fixture values',()=>{const radio=entries.find(e=>e.name==='Radio')!;expect(radio.props.find(p=>p.name==='modelValue')?.sourceDefault).toBe('未指定');expect(radio.props.find(p=>p.name==='options')?.sourceDefault).toBe('必填');expect(entries.find(e=>e.name==='Tabs')?.props.find(p=>p.name==='modelValue')?.sourceDefault).toBe('必填');expect(entries.find(e=>e.name==='Switch')?.props.find(p=>p.name==='label')?.sourceDefault).toBe('"开关"')})
import {createExample} from '../code-examples'
it('copied dialogs and menus have a usable trigger and explicit open event',()=>{for(const name of ['Dialog','Drawer','ConfirmDialog','Dropdown']){const code=createExample(entries.find(e=>e.name===name)!,{});expect(code).toContain("import Button from '@/components/ui/Button.vue'");expect(code).toContain('<Button');expect(code).toContain('open=true');if(name==='Dropdown')expect(code).toContain('clientX')}})
import {stories,storyDefaults} from '../stories'
it('second-precision alias examples preserve the displayed public precision',()=>{for(const name of ['TimePickerSeconds','DateTimePickerSeconds']){const e=entries.find(e=>e.name===name)!;const story=stories.find(s=>s.componentId===e.id&&s.storyId==='basic')!;expect(createExample(e,storyDefaults(e,story))).toContain('precision="second"')}})
