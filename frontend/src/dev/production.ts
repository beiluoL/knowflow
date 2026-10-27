import type {PlaygroundEntry,PropSpec} from './registry'
export const implementationStates=['PRODUCTION','DEMO_ONLY','PLANNED','DEPRECATED'] as const
export type ImplementationState=typeof implementationStates[number]
export const evidenceDimensions=['Types','Unit Tests','Interaction','Accessibility','Visual','Responsive','Integration'] as const
export interface ProductionMetadata {status:ImplementationState;package:string;model:string;limitations:string[];consumers:string[];changed:string;quality:Record<typeof evidenceDimensions[number],{status:'NOT VERIFIED'|'PASS'|'FAIL';evidence:string}>;promotion:string[]}
export function productionMetadata(entry:PlaygroundEntry):ProductionMetadata{
 const status=entry.implementationStatus==='已接入真实组件'?'PRODUCTION':entry.implementationStatus==='仅有内部演示'?'DEMO_ONLY':'PLANNED'
 return {status,package:status==='PRODUCTION'?'knowflow-frontend（未独立发布）':'dev-only',model:entry.events.includes('update:modelValue')?'modelValue / update:modelValue':'无已登记v-model',limitations:controlLimits[entry.name]||[status==='PRODUCTION'?'以源码公开API为准；生产实现不代表完整认证。':'仅开发Fixture，禁止在业务页引用。'],consumers:newControls.includes(entry.name)?['/dev/ui-components（尚未迁移业务页）']:['未逐页验证使用方'],changed:newControls.includes(entry.name)?'2026-10-25 V6：独立公共实现、受控事件及真实预览':'V6核对当前源码；历史实现不计为本轮新增',quality:Object.fromEntries(evidenceDimensions.map(key=>[key,{status:'NOT VERIFIED',evidence:''}])) as ProductionMetadata['quality'],promotion:newControls.includes(entry.name)?['Demo · V5参考实现保留','Candidate · 独立API与回归测试','Production · 当前公共源码；验收见V6_TEST_REPORT','Verified · 待完整人工读屏/集成认证']:['当前实现状态：'+status,'Verified：未提供完整认证证据']}
}
export const newControls=['Radio','Switch','Tabs','SearchInput']
export const controlLimits:Record<string,string[]>={Radio:['受控string | number；原生单选组键盘行为；readonly阻止变更。','不支持异步选项或自定义复杂选项插槽。'],Switch:['受控boolean；loading/disabled阻止变更；readonly保留可聚焦语义。','不内置API请求、保存或失败回滚。'],Tabs:['水平方向自动激活；方向键/Home/End跳过disabled；具名panel插槽。','不支持纵向、路由同步、拖拽或关闭标签。'],SearchInput:['真实Input/Button组合；Enter/按钮search；IME保护、清空及受控值。','调用方负责请求、取消、节流与建议；不是Combobox。']}
const prop=(name:string,type:string,value:unknown,editable=true):PropSpec=>({name,type,default:value,sourceDefault:JSON.stringify(value)??'未指定',required:false,description:'真实公开API；'+name,editable})
export function controlProps(name:string):PropSpec[]{return fixtureControlProps(name).map(p=>{
 const sourceDefaults:Record<string,Record<string,string>>={Radio:{modelValue:'未指定',options:'必填',name:'未指定',label:'未指定',error:'未指定'},Switch:{label:'"开关"'},Tabs:{modelValue:'必填',items:'必填',label:'"内容标签"'},SearchInput:{label:'"搜索"',placeholder:'"输入关键词"',error:'未指定'}}
 return {...p,sourceDefault:sourceDefaults[name]?.[p.name]??p.sourceDefault}
})}
function fixtureControlProps(name:string):PropSpec[]{
 const common=[prop('disabled','boolean',false),prop('readonly','boolean',false)]
 if(name==='Radio')return [prop('modelValue','string | number','all',false),{...prop('options','RadioOption[]',[],false),required:true},prop('name','string','fixture-radio'),prop('label','string','学习范围'),...common,prop('error','string','')]
 if(name==='Switch')return [prop('modelValue','boolean',false),prop('label','string','启用本地选项'),...common,prop('loading','boolean',false)]
 if(name==='Tabs')return [{...prop('modelValue','string','overview',false),required:true},{...prop('items','TabItem[]',[],false),required:true},prop('label','string','本地内容标签')]
 return [prop('modelValue','string','',false),prop('label','string','搜索本地笔记'),prop('placeholder','string','输入关键词'),...common,prop('loading','boolean',false),prop('error','string','')]
}
