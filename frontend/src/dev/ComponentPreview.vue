<template>
 <div ref="host" class="pg-preview" data-testid="component-preview">
  <ProductionControlsPreview v-if="['Radio','Switch','Tabs','SearchInput'].includes(entry.name)" :name="entry.name" :settings="settings" @event="record"/>
  <ExtensionDemos v-else-if="extensionNames.includes(entry.name)" :key="entry.name" :name="entry.name" :settings="settings" @event="record"/>
  <ScenarioPreview v-else-if="scenarios.includes(entry.name)" :name="entry.name" :fixture="settings" @event="record"/>
  <ReferenceDemos v-else-if="entry.implementationStatus==='仅有内部演示'" :key="entry.name" :name="entry.name" :disabled="disabled" :fixture="settings" @event="record"/>
  <p v-else-if="entry.implementationStatus==='尚未实现'">尚未实现，未伪造预览。</p>
  <template v-else-if="name==='ToastHost'"><div class="pg4-row"><Button v-for="kind in toastKinds" :key="kind" @click="toast(kind)">{{kind}} 通知</Button><Button variant="text" @click="clearToasts">清理本页通知</Button></div><p>真实ToastHost位于应用全局；只移除本页创建的通知。</p></template>
  <template v-else-if="name==='Button'"><Button :size="size" :variant="variant" :icon-only="!!settings.iconOnly" :aria-label="settings.iconOnly?String(settings.ariaLabel||'搜索示例'):undefined" :disabled="disabled" :loading="loading" :icon-name="icon?String(settings.iconName||'plus'):undefined" :icon-position="rightIcon?'right':'left'" @click="count++;record('click',{count})">{{label}}</Button><p>本地点击 {{count}} 次</p><div class="pg4-row"><Button v-for="v in variants" :key="v" :variant="v" size="sm">{{v}}</Button><Button icon-only icon-name="search" aria-label="搜索预览"/></div><div class="pg4-row"><Button v-for="s in sizes" :key="s" :size="s">{{s}}</Button><Button disabled>不可操作</Button><Button loading>正在处理</Button></div></template>
  <IconGallery v-else-if="name==='Icon'" :preview-size="Number(settings.size)||16"/>
  <div v-else-if="name==='Checkbox'" class="pg4-stack"><Checkbox v-model="checked" :disabled="disabled" :indeterminate="!!settings.indeterminate" :label="label" @change="record('change',$event)"/><Checkbox indeterminate label="部分选中"/><Checkbox :model-value="true" disabled label="已禁用"/><div class="pg4-row"><Checkbox v-for="v in ['Java','SQL','Vue']" :key="v" v-model="group" :value="v" :label="v" @change="record('group-change',group)"/></div><p>多选值：{{group}}</p></div>
  <div v-else-if="name==='FormField'" :class="settings.singleColumn?'pg4-stack':'kf-form-row'"><Input v-model="text" label="短标签" help="帮助说明" :required="!!settings.required"/><Input v-model="other" label="更长的字段标签，验证多列轨道" :error="error?'请检查此字段':''"/><Select v-model="choice" label="多列表单选择" :options="options"/></div>
  <Select v-else-if="name==='Select'" :key="String(settings.multiple)" v-model="selectValue" label="知识库（本地）" :options="settings.empty?[]:options" :multiple="!!settings.multiple" :searchable="settings.searchable!==false" :clearable="!!settings.clearable" :disabled="disabled" :loading="loading" :error="error?'选择错误示例':''" @change="record('change',$event)"/>
  <AIComposer v-else-if="name==='AIComposer'" v-model="text" :busy="loading" :stoppable="!!settings.stoppable" :disabled="disabled" :error="error?'本地错误示例':''" @submit="record('submit',text)" @stop="record('stop',null)"/>
  <template v-else-if="['AppDialog','ConfirmDialog'].includes(name)"><Button @click="open=true;record('open',true)">打开本地确认</Button><component :is="entry.component" :open="open" title="确认本地操作？" description="仅更改展示状态，不会删除数据。" :placement="entry.name==='Drawer'?'right':'center'" scrollable :loading="loading" :disabled="disabled" :width="settings.width?String(settings.width):undefined" :error="error?'Fixture错误，可重试':''" :variant="settings.variant==='danger'?'danger':'default'" @cancel="open=false;record('cancel',null)" @confirm="open=false;record('confirm','仅本地')"><Input v-model="text" label="本地表单"/><div v-if="settings.longContent"><p v-for="i in 30" :key="i">长内容第{{i}}段：仅验证滚动。</p></div></component><p v-if="entry.name==='Drawer'">真实AppDialog仅支持Right；Left：Not Supported。</p></template>
  <template v-else-if="name==='ContextMenu'"><Button @click="showMenu" @contextmenu.prevent="showMenu">更多操作（支持右键）</Button><component :is="entry.component" :open="open" :x="x" :y="y" :items="menuItems" @close="open=false;record('close',null)" @select="record('select',$event)"/></template>
  <component v-else-if="name==='Card'" :is="entry.component" :variant="settings.variant||'flat'"><template #header>真实Card标题</template><p>本地统计内容 · 42条Fixture（非用户数据）</p><template #footer><Button size="sm" variant="text" @click="record('fixture-card-action',null)">本地操作</Button></template></component>
  <div v-else-if="name==='TagsInput'" class="pg4-stack"><KfTagsInput :model-value="tagsValue" :placeholder="String(settings.placeholder||'输入标签后按 Enter')" :disabled="disabled" :readonly="!!settings.readonly" :error="error?'请检查标签输入':''" :max-tags="Number(settings.maxTags)||0" :max-tag-length="Number(settings.maxTagLength)||0" :allow-duplicates="!!settings.allowDuplicates" :size="size" :clearable="!!settings.clearable" aria-label="添加标签（本地预览）" @update:model-value="onTagsUpdate" @add="record('add',$event)" @remove="record('remove',$event)" @invalid="record('invalid',$event)" @clear="record('clear',null)" @focus="record('focus','输入聚焦')" @blur="record('blur','输入失焦')"/><p>当前标签 {{tagsValue.length}} 个：{{tagsValue.join(' / ')||'空数组'}}</p><p>输入后按 Enter 添加；空输入按 Backspace 删除末尾；点击 × 单独删除。</p></div>
  <Pagination v-else-if="name==='Pagination'" :page-num="page" :page-size="Number(settings.pageSize)||10" :total="50" @change="page=$event;record('change',$event)"/>
  <component v-else-if="entry.component" :is="entry.component" v-bind="demoProps" @update:model-value="onModelUpdate($event)" @change="record('change',$event)" @focus="record('focus','控件聚焦')" @blur="record('blur','控件失焦')">{{name==='Card'?'真实卡片，本地内容':name==='Badge'?'已完成':undefined}}</component>
 </div>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount,defineAsyncComponent} from 'vue'
import {notify,dismiss,toastState,type ToastType} from '@/utils/toast'
import Button from '@/components/ui/Button.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import KfTagsInput from '@/components/ui/KfTagsInput.vue'
import AIComposer from '@/components/ai/AIComposer.vue'
import Pagination from '@/components/ui/Pagination.vue'
import ProductionControlsPreview from './ProductionControlsPreview.vue'
import ReferenceDemos from './ReferenceDemos.vue'
import ExtensionDemos from './ExtensionDemos.vue'
import {extensionNames} from './extensions-v5'
import IconGallery from './IconGallery.vue'
import type {PlaygroundEntry} from './registry'
const ScenarioPreview=defineAsyncComponent(()=>import('./ScenarioPreview.vue'))
const props=withDefaults(defineProps<{entry:PlaygroundEntry;size:'sm'|'md'|'lg';variant:'primary'|'secondary'|'outline'|'ghost'|'text'|'danger';disabled:boolean;loading:boolean;icon:boolean;rightIcon:boolean;error:boolean;label:string;settings?:Record<string,unknown>}>(),{settings:()=>({})})
const emit=defineEmits<{event:[name:string,value:unknown]}>(),record=(name:string,value:unknown)=>emit('event',name,value)
const name=computed(()=>props.entry.previewKey||props.entry.name),scenarios=['AIChat','RAG','Agent','Flashcard','SmartQuiz','Interview','TechEnglishTerm','NoteEditorDialog'],host=ref<HTMLElement>(),text=ref(''),other=ref(''),checked=ref(false),group=ref<string[]>([]),choice=ref('all'),count=ref(0),page=ref(1),open=ref(false),x=ref(0),y=ref(0),single=ref('all'),multi=ref<string[]>([]),tagsValue=ref<string[]>(['Vue','TypeScript'])
const dateValue=ref('2026-10-15')
const yearValue=ref('2026'),monthValue=ref('2026-10')
const timeValue=ref('09:30'),timeSecondValue=ref('09:30:45')
const dateTimeValue=ref('2026-10-25T09:30'),dateTimeSecondValue=ref('2026-10-25T09:30:45')
const dateRangeValue=ref({start:'2026-10-05',end:'2026-10-15'})
const timeRangeValue=ref({start:'09:00',end:'18:00'})
const dateTimeRangeValue=ref({start:'2026-10-28T09:30',end:'2026-11-05T11:30'})
type RangeFixture={start:string;end:string}
function onModelUpdate(value:unknown):void{
 switch(name.value){
  case 'DatePicker':case 'DayPicker':dateValue.value=String(value??'');break
  case 'YearPicker':yearValue.value=String(value??'');break
  case 'MonthPicker':monthValue.value=String(value??'');break
  case 'TimePicker':timeValue.value=String(value??'');break
  case 'TimePickerSeconds':timeSecondValue.value=String(value??'');break
  case 'DateTimePicker':dateTimeValue.value=String(value??'');break
  case 'DateTimePickerSeconds':dateTimeSecondValue.value=String(value??'');break
  case 'DateRangePicker':dateRangeValue.value=value as RangeFixture;break
  case 'TimeRangePicker':timeRangeValue.value=value as RangeFixture;break
  case 'DateTimeRangePicker':dateTimeRangeValue.value=value as RangeFixture;break
  default:text.value=String(value??'')
 }
 record('update:modelValue',value)
}
function onTagsUpdate(value: string[]): void { tagsValue.value = value; record('update:modelValue', value) }
watch(()=>props.settings.modelValue,value=>{if(typeof value==='string')text.value=value;if(typeof value==='boolean')checked.value=value;if(name.value==='DatePicker'&&typeof value==='string'&&value)dateValue.value=value;if(name.value==='TagsInput'&&Array.isArray(value))tagsValue.value=[...(value as string[])];if(Array.isArray(value))multi.value=value as string[];else if(name.value==='Select'&&typeof value==='string'&&value)single.value=value},{immediate:true})
const selectValue=computed({get:()=>props.settings.multiple?multi.value:single.value,set:(v:unknown)=>{if(Array.isArray(v))multi.value=v as string[];else single.value=String(v??'')}})
const sizes=['sm','md','lg'] as const,variants=['primary','secondary','outline','ghost','text','danger'] as const,toastKinds:ToastType[]=['success','info','warning','error']
const options=[{value:'all',label:'全部知识库',group:'全部'},{value:'java',label:'Java 与人工智能学习资料（本地长名称）',group:'学习'},{value:'ui',label:'UI 设计系统',group:'学习'},{value:'disabled',label:'禁用选项',disabled:true}]
const menuItems=[{key:'copy',label:'复制本地示例',icon:'copy',onSelect:()=>record('fixture-action','copy')},{key:'disabled',label:'禁用操作',disabled:true},{key:'delete',label:'危险操作示例',danger:true,separator:true,onSelect:()=>record('fixture-action','不删除任何数据')}]
function showMenu(e:MouseEvent){x.value=e.clientX;y.value=e.clientY;open.value=true}
const ownToasts=new Set<number>(),timers=new Set<ReturnType<typeof setTimeout>>()
function toast(type:ToastType){const before=new Set(toastState.toasts.map(t=>t.id));notify('[Playground Fixture] 本地通知',type,0);for(const t of toastState.toasts)if(!before.has(t.id)){ownToasts.add(t.id);const timer=setTimeout(()=>{dismiss(t.id);ownToasts.delete(t.id);timers.delete(timer)},2500);timers.add(timer)}record('fixture-toast',type)}
function clearToasts(){for(const id of ownToasts)dismiss(id);ownToasts.clear();for(const t of timers)clearTimeout(t);timers.clear()}
watch(()=>props.entry.name,()=>{open.value=false;text.value='';count.value=0;clearToasts()})
onBeforeUnmount(clearToasts)
const demoProps=computed(()=>{
 const s=props.settings,common={disabled:props.disabled,error:props.error?'请检查输入':'',readonly:!!s.readonly,required:!!s.required}
 switch(name.value){
 case 'Input':return {...common,modelValue:text.value,label:String(s.label||'名称'),placeholder:String(s.placeholder||'输入本地内容'),type:String(s.type||'text'),prefixIconName:'search',suffixIconName:'check'}
 case 'Textarea':return {...common,modelValue:text.value,label:String(s.label||'说明'),placeholder:'输入多行内容',rows:Number(s.rows)||4,maxlength:Number(s.maxlength)||300,showCount:s.showCount!==false,autoResize:s.autoResize!==false,minHeight:Number(s.minHeight)||96,maxHeight:Number(s.maxHeight)||320}
 case 'DatePicker':return {...common,modelValue:dateValue.value,label:'日期',clearable:!!s.clearable,min:String(s.min||'2026-10-01'),max:String(s.max||'2026-10-31')}
 case 'DayPicker':return {...common,modelValue:dateValue.value,label:'具体日期',clearable:!!s.clearable,min:String(s.min||'2026-10-01'),max:String(s.max||'2026-10-31')}
 case 'YearPicker':return {...common,modelValue:yearValue.value,label:'年份',minYear:Number(s.minYear)||2020,maxYear:Number(s.maxYear)||2035}
 case 'MonthPicker':return {...common,modelValue:monthValue.value,label:'年月',minMonth:String(s.minMonth||'2026-01'),maxMonth:String(s.maxMonth||'2027-12')}
 case 'TimePicker':return {...common,modelValue:timeValue.value,label:'时间',precision:s.precision==='second'?'second':'minute',clearable:!!s.clearable,min:String(s.min||'08:00'),max:String(s.max||'22:00')}
 case 'TimePickerSeconds':return {...common,modelValue:timeSecondValue.value,label:'时间（秒）',precision:'second',clearable:!!s.clearable,min:String(s.min||'08:00'),max:String(s.max||'22:00')}
 case 'DateTimePicker':return {...common,modelValue:dateTimeValue.value,label:'日期时间',precision:s.precision==='second'?'second':'minute',clearable:!!s.clearable,min:String(s.min||'2026-10-01T00:00'),max:String(s.max||'2026-10-31T23:59')}
 case 'DateTimePickerSeconds':return {...common,modelValue:dateTimeSecondValue.value,label:'日期时间（秒）',precision:'second',clearable:!!s.clearable,min:String(s.min||'2026-10-01T00:00:00'),max:String(s.max||'2026-10-31T23:59:59')}
 case 'DateRangePicker':return {...common,modelValue:dateRangeValue.value,label:'日期范围',clearable:!!s.clearable,min:String(s.min||'2026-10-01'),max:String(s.max||'2026-10-31')}
 case 'TimeRangePicker':return {...common,modelValue:timeRangeValue.value,label:'时间范围',precision:'minute',allowOvernight:!!s.allowOvernight,clearable:!!s.clearable,min:String(s.min||'08:00'),max:String(s.max||'22:00')}
 case 'DateTimeRangePicker':return {...common,modelValue:dateTimeRangeValue.value,label:'日期时间范围',precision:'minute',clearable:!!s.clearable,min:String(s.min||'2026-09-01T00:00'),max:String(s.max||'2026-12-31T23:59')}
 case 'Progress':return {percentage:Number(s.percentage??64),showLabel:s.showLabel!==false,label:'Fixture进度',variant:s.variant||'primary'}
 case 'SkeletonList':return {rows:Number(s.rows)||2,type:['list','card','line'].includes(String(s.type))?s.type:'line'}
 case 'Badge':return {variant:s.variant||'success'}
 case 'Card':return {variant:s.variant||'flat'}
 case 'EmptyState':return {title:'尚无内容',description:'只有本地示例'}
 case 'ErrorState':return {title:'加载失败',message:'仅预览错误状态'}
 case 'CodeBlock':return {code:'// Fixture\n'+Array.from({length:25},(_,i)=>`const value${i} = ${i}`).join('\n'),lang:'ts'}
 case 'MarkdownRenderer':return {content:'# Fixture 文档\n## 标题\n- 列表\n\n> 本地引用\n\n|字段|值|\n|---|---|\n|状态|Fixture|\n\n```java\nclass Demo {}\n```\n```sql\nSELECT 1;\n```\n```json\n{"fixture":true}\n```'}
 default:return {}
 }
})
defineExpose({host})
</script>
