import {scenarioLinks,scenarioStates} from './scenarios-v6'
import {entries,type PlaygroundEntry} from './registry'
export interface ComponentStory {
 storyId:string;componentId:string;title:string;description:string;category:'Basic'|'Variants'|'Sizes'|'States'|'Interactive'|'Form'|'Edge Cases'|'Accessibility'|'Real-world Example';implementationStatus:PlaygroundEntry['implementationStatus'];initialProps:Record<string,unknown>;slots:Record<string,string>;fixture:Record<string,unknown>;expectedEvents:string[];interactionSteps:string[];expectedVisualState:string;supportedViewport:string[];accessibilityNotes:string
}
function story(entry:PlaygroundEntry,id:string,title:string,initialProps:Record<string,unknown>={},category:ComponentStory['category']='States',extra:Partial<ComponentStory>={}):ComponentStory{return{storyId:id,componentId:entry.id,title,description:`${entry.title} · ${title}，局部状态，非业务请求。`,category,implementationStatus:entry.implementationStatus,initialProps,slots:{},fixture:{},expectedEvents:entry.events,interactionSteps:['Tab进入真实控件','按其语义操作；观察本地事件'],expectedVisualState:'NOT VERIFIED · 需客观测量或独立交互证据',supportedViewport:['desktop','tablet','mobile'],accessibilityNotes:entry.accessibilityNotes,...extra}}
export function buildStories(catalog:PlaygroundEntry[]):ComponentStory[]{return catalog.flatMap(entry=>{
 const result=[story(entry,'basic','基础预览',{},'Basic')],supports=(name:string)=>entry.props.some(p=>p.name===name)
 for(const key of ['disabled','loading','readonly','required','indeterminate'])if(supports(key))result.push(story(entry,key,key,{[key]:true}))
 if(scenarioLinks.some(s=>s.id===entry.name))for(const state of scenarioStates)result.push(story(entry,'scene-'+state,state+' · 业务Fixture',{},'Real-world Example',{fixture:{ui:{scenarioState:state}}}))
 if(entry.name==='Button'){
  for(const size of ['sm','md','lg'])result.push(story(entry,'size-'+size,size+' 尺寸',{size},'Sizes'))
  for(const variant of entry.variants)result.push(story(entry,'variant-'+variant,variant+' 变体',{variant},'Variants'))
  result.push(story(entry,'icon-right','右侧图标',{iconName:'plus',iconPosition:'right'},'Variants'))
  if(supports('iconOnly'))result.push(story(entry,'icon-only','纯图标',{iconOnly:true,iconName:'search',ariaLabel:'搜索示例'},'Accessibility'))
  result.push(story(entry,'long-label','长标签',{},'Edge Cases',{slots:{default:'查看当前知识库的学习与交互规范'}}))
 }
 if(['Input','Textarea'].includes(entry.name))result.push(story(entry,'long-text','长文本与输入', {modelValue:'这是仅用于Story预览的长文本。'.repeat(12)},'Edge Cases'))
 if(entry.name==='TagsInput'){
  result.push(story(entry,'multiple-tags','多标签',{modelValue:['Vue','TypeScript','设计 Token','键盘导航','IME','无障碍']},'Variants'))
  result.push(story(entry,'empty','空标签',{modelValue:[]},'Variants'))
  result.push(story(entry,'long-tags','中文/英文长标签',{modelValue:['这是一个用于验证自动换行与省略的中文长标签','A-very-long-english-tag-without-any-spaces','SQL']},'Edge Cases'))
  result.push(story(entry,'many-tags','20 个标签',{modelValue:Array.from({length:20},(_,i)=>`标签 ${i+1}`)},'Edge Cases'))
  result.push(story(entry,'max-tags','达到数量上限',{maxTags:3,modelValue:['Vue','TypeScript','SQL']},'Edge Cases'))
  result.push(story(entry,'ime','中文 IME 组合输入',{modelValue:[]},'Interactive',{interactionSteps:['聚焦真实 input','使用中文输入法输入拼音，组合期间按 Enter 不应添加标签','确认候选后按 Enter 才添加'],expectedVisualState:'NOT VERIFIED · 需真实输入法会话'}))
 }
 if(supports('error'))result.push(story(entry,'error','错误提示',{error:'Fixture：请检查本地输入'},'Form'))
 if(entry.name==='Select'){result.push(story(entry,'multi-search','多选搜索',{multiple:true,searchable:true,clearable:true,modelValue:['java']},'Interactive'));result.push(story(entry,'empty','空选项',{},'Edge Cases',{fixture:{ui:{empty:true}}}))}
 if(['Dialog','Drawer','ConfirmDialog','AppDialog'].includes(entry.name))result.push(story(entry,'long-content','长内容与焦点',{},'Edge Cases',{fixture:{ui:{longContent:true}}}))
 if(entry.name==='Table')for(const [id,ui] of [['empty',{tableEmpty:true}],['fixture-loading',{busy:true}]] as const)result.push(story(entry,id,id,{},'States',{fixture:{ui}}))
 if(entry.name==='Combobox'||entry.name==='SearchInput'&&entry.implementationStatus==='仅有内部演示')for(const [id,ui] of [['fixture-loading',{searchLoading:true}],['fixture-error',{searchError:true}],['no-results',{query:'没有匹配',open:true}]] as const)result.push(story(entry,id,id,{},'States',{fixture:{ui}}))
 if(entry.name==='Flashcard')for(const [id,ui] of [['empty',{empty:true}],['fixture-loading',{cardLoading:true}]] as const)result.push(story(entry,id,id,{},'States',{fixture:{ui}}))
 if(entry.name==='RAG')result.push(story(entry,'no-answer','证据不足（Fixture）',{},'Edge Cases',{fixture:{ui:{empty:true}}}))
 if(entry.name==='Agent')for(const toolStatus of ['success','timeout'])result.push(story(entry,'fixture-'+toolStatus,toolStatus+'（模拟工具状态）',{},'States',{fixture:{ui:{toolStatus}}}))
 if(entry.implementationStatus==='仅有内部演示')result.push(story(entry,'interactive','交互参考',{},'Interactive',{interactionSteps:['操作画布中的本地Fixture控件','重置或切换Story验证状态隔离']}))
 return result
})}
export const stories=buildStories(entries)
export function validateStories(catalog:PlaygroundEntry[],list:ComponentStory[]):string[]{const errors:string[]=[],seen=new Set<string>();for(const s of list){const e=catalog.find(e=>e.id===s.componentId),key=s.componentId+':'+s.storyId;if(seen.has(key))errors.push('duplicate:'+key);seen.add(key);if(!e){errors.push('component:'+s.componentId);continue}for(const [name,value] of Object.entries(s.initialProps)){const p=e.props.find(p=>p.name===name);if(!p||e.name==='Button'&&name==='label'){errors.push('prop:'+key+':'+name);continue}if(p.options&&!p.options.includes(String(value)))errors.push('option:'+key+':'+name);if(p.type==='boolean'&&typeof value!=='boolean')errors.push('boolean:'+key+':'+name)}for(const slot of Object.keys(s.slots))if(!e.slots.includes(slot))errors.push('slot:'+key+':'+slot)}return errors}
export function resolveStory(catalog:PlaygroundEntry[],list:ComponentStory[],component:unknown,storyId:unknown){const id=typeof component==='string'?component.toLowerCase():'';const entry=catalog.find(e=>e.id.toLowerCase()===id)||catalog.find(e=>e.name==='Button')||catalog[0];const own=list.filter(s=>s.componentId===entry?.id),story=own.find(s=>s.storyId===storyId)||own[0];return{entry,story}}
export function storyDefaults(entry:PlaygroundEntry,story:ComponentStory):Record<string,unknown>{const defaults=Object.fromEntries(entry.props.map(p=>[p.name,p.default]));return structuredClone({...defaults,...story.initialProps,...(story.fixture.ui as Record<string,unknown>||{}),...(story.slots.default?{label:story.slots.default}:{})})}
