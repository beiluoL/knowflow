import {controlExample} from './control-examples-v6'
import type {PlaygroundEntry} from './registry'
const quoteAttr=(value:string)=>'\"'+value.replaceAll('&','&amp;').replaceAll('\"','&quot;').replaceAll('<','&lt;')+'\"'
export function createExample(entry:PlaygroundEntry,settings:Record<string,unknown>):string{
 const control=controlExample(entry,settings);if(control)return control
 if(entry.implementationStatus==='尚未实现')return '<!-- 尚未实现；没有可运行组件，不生成虚假使用代码。 -->'
 if(entry.implementationStatus==='仅有内部演示'){
  const component=entry.sourcePath.split('/').pop()!.replace('.vue','')
  const fixture=Object.fromEntries(['tableEmpty','busy','searchLoading','searchError','empty','cardLoading','toolStatus','query','open','scenarioState'].filter(key=>settings[key]!==undefined).map(key=>[key,settings[key]]))
  const fixtureAttr=Object.keys(fixture).length&&component!=='ExtensionDemos'?' :fixture='+quoteAttr(JSON.stringify(fixture)):''
  return `<script setup lang="ts">\n// 演示组件 / 非生产组件；只能在开发展示页引用，禁止业务页面使用\nimport ${component} from '@/${entry.sourcePath.replace('frontend/src/','')}'\n// 接口草案：${entry.props.map(p=>p.name+': '+p.type).join(', ')||'name选择本地示例，内部ref管理状态'}\n<`+`/script>\n<template><${component} name="${entry.name}"${fixtureAttr} /></template>`
 }

 const name=entry.sourcePath.split('/').pop()!.replace('.vue',''),model=['Input','Textarea','DatePicker','DayPicker','YearPicker','MonthPicker','TimePicker','TimePickerSeconds','DateTimePicker','DateTimePickerSeconds','DateRangePicker','TimeRangePicker','DateTimeRangePicker','Checkbox','Select','TagsInput','AIComposer'].includes(entry.name)
 const declarations:Record<string,string>={Checkbox:'const value=ref(false)',TagsInput:"const value=ref<string[]>(['Vue','TypeScript'])",Select:"const value=ref('all')\nconst options=[{value:'all',label:'全部知识库'},{value:'java',label:'Java学习资料'}]",DatePicker:"const value=ref('2026-10-15')",DayPicker:"const value=ref('2026-10-15')",YearPicker:"const value=ref('2026')",MonthPicker:"const value=ref('2026-10')",TimePicker:"const value=ref('09:30')",TimePickerSeconds:"const value=ref('09:30:45')",DateTimePicker:"const value=ref('2026-10-25T09:30')",DateTimePickerSeconds:"const value=ref('2026-10-25T09:30:45')",DateRangePicker:"const value=ref({start:'2026-10-05',end:'2026-10-15'})",TimeRangePicker:"const value=ref({start:'09:00',end:'18:00'})",DateTimeRangePicker:"const value=ref({start:'2026-10-25T09:00',end:'2026-10-25T18:00'})",Pagination:'const page=ref(1)',Dialog:'const open=ref(false)',Drawer:'const open=ref(false)',ConfirmDialog:'const open=ref(false)',AppDialog:'const open=ref(false)',Dropdown:"const open=ref(false),x=ref(0),y=ref(0)\nconst items=[{key:'copy',label:'仅本地操作',onSelect:()=>{open.value=false}}]",ContextMenu:"const open=ref(false),x=ref(0),y=ref(0)\nconst items=[{key:'copy',label:'仅本地操作',onSelect:()=>{open.value=false}}]"}
 let setup=declarations[entry.name]||(model?"const value=ref('')":'');if(entry.name==='Select'&&settings.multiple)setup=setup.replace("ref('all')",'ref<string[]>([])');if(model&&settings.modelValue!==undefined&&settings.modelValue!==''){const literal=JSON.stringify(settings.modelValue).replaceAll('</','<\\/');setup=setup.replace(/const value=ref(?:<[^>]+>)?\([^\n]*\)/,'const value=ref('+literal+')')}const attrs:string[]=[]
 if(model)attrs.push('v-model="value"')
 for(const prop of entry.props.filter(p=>p.editable&&!['label','modelValue',...(entry.name==='Icon'?['name','size']:[]),...(entry.name==='Pagination'?['pageSize']:[])].includes(p.name))){const value=settings[prop.name];if(value===undefined||value==='')continue;attrs.push(typeof value==='boolean'?`:${prop.name}="${value}"`:typeof value==='number'?`:${prop.name}="${value}"`:`${prop.name}=${quoteAttr(String(value))}`)}
 if(['Empty','EmptyState'].includes(entry.name))attrs.push('title="暂无内容"')
 if(entry.name==='Select')attrs.push(':options="options"')
 if(['Dialog','Drawer','AppDialog','ConfirmDialog'].includes(entry.name))attrs.push(':open="open"','title="本地示例"','@cancel="open=false"','@confirm="open=false"',...(entry.name==='Drawer'?['placement="right"']:[]))
 if(['Dropdown','ContextMenu'].includes(entry.name))attrs.push(':open="open"',':x="x"',':y="y"',':items="items"','@close="open=false"')
 if(entry.name==='Pagination')attrs.push(':page-num="page"',':page-size="'+(Number(settings.pageSize)||10)+'"',':total="50"','@change="page=$event"')
 if(entry.name==='Icon')attrs.push('name="search"',':size="'+(settings.size||16)+'"')
 if(entry.name==='Progress'&&!attrs.some(a=>a.includes('percentage')))attrs.push(':percentage="64"')
 if(entry.name==='MarkdownRenderer')attrs.push('content="## Fixture 文档"')
 if(entry.name==='CodeBlock')attrs.push('code="const ready = true"','lang="ts"')
 if(entry.name==='FormField')attrs.push('control-id="fixture-name"','label="名称"')
 if(entry.name==='Toast'||entry.name==='ToastHost')return `<script setup lang="ts">\nimport {notify} from '@/utils/toast'\nimport Button from '@/components/ui/Button.vue'\n// 复用应用全局ToastHost；展示页会清理自己的通知\n<`+`/script>\n<template><Button @click="notify('[Fixture] 本地通知','info')">显示通知</Button></template>`
 const trigger=['Dialog','Drawer','AppDialog','ConfirmDialog'].includes(entry.name)?'<Button @click="open=true">打开本地弹窗</Button>': ['Dropdown','ContextMenu'].includes(entry.name)?'<Button @click="x=$event.clientX;y=$event.clientY;open=true">打开本地菜单</Button>':''
 const text=String(settings.label||'本地示例').replaceAll('&','&amp;').replaceAll('<','&lt;'),slot=entry.name==='Button'?text:entry.name==='FormField'?'<input id="fixture-name" />':['Card','Badge'].includes(entry.name)?'Fixture内容':''
 if(['Input','Textarea'].includes(entry.name))attrs.push('label='+quoteAttr(String(settings.label||'名称')))

 if(entry.name==='Checkbox')attrs.push(`label=${quoteAttr(text)}`)
 return `<script setup lang="ts">\n${setup?"import {ref} from 'vue'\n":''}import ${name} from '@/${entry.sourcePath.replace('frontend/src/','')}'\n${trigger?"import Button from '@/components/ui/Button.vue'\n":''}// 仅本地示例，不连接业务API\n${setup}\n<`+`/script>\n<template>\n  ${trigger}\n  <${name} ${attrs.join(' ')}>${slot}</${name}>\n</template>`
}
