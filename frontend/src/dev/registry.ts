import {productionMetadata,controlProps,newControls,type ProductionMetadata} from './production'
import {markRaw,defineAsyncComponent,type Component} from 'vue'
import Radio from '@/components/ui/Radio.vue'
import Switch from '@/components/ui/Switch.vue'
import Tabs from '@/components/ui/Tabs.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
import Progress from '@/components/ui/Progress.vue'
import SkeletonList from '@/components/ui/SkeletonList.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import ToastHost from '@/components/ui/ToastHost.vue'
import Button from '@/components/ui/Button.vue'
import Icon from '@/components/ui/Icon.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import KfTagsInput from '@/components/ui/KfTagsInput.vue'
import Select from '@/components/ui/Select.vue'
import DatePicker from '@/components/ui/DatePicker.vue'
import YearPicker from '@/components/ui/YearPicker.vue'
import MonthPicker from '@/components/ui/MonthPicker.vue'
import TimePicker from '@/components/ui/TimePicker.vue'
import DateTimePicker from '@/components/ui/DateTimePicker.vue'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'
import TimeRangePicker from '@/components/ui/TimeRangePicker.vue'
import DateTimeRangePicker from '@/components/ui/DateTimeRangePicker.vue'
import FormField from '@/components/ui/FormField.vue'
import Badge from '@/components/ui/Badge.vue'
import Card from '@/components/ui/Card.vue'
import Pagination from '@/components/ui/Pagination.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import ContextMenu from '@/components/ui/ContextMenu.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import AIComposer from '@/components/ai/AIComposer.vue'
const MarkdownRenderer=defineAsyncComponent(()=>import('@/components/chat/ChatMarkdownRenderer.vue'))
const CodeBlock=defineAsyncComponent(()=>import('@/components/chat/ChatCodeBlock.vue'))
import {buildCatalog} from './catalog-v4'
import {extensionEntries} from './extensions-v5'
export interface BaseEntry {name:string;title:string;category:string;description:string;component?:Component;supportedProps:string[];variants:string[];examples:string;accessibilityNotes:string;sourcePath:string}
export interface PropSpec{name:string;type:string;default:unknown;sourceDefault:string;required:boolean;description:string;options?:string[];editable:boolean}
export interface PlaygroundEntry extends BaseEntry{production?:ProductionMetadata;id:string;previewKey:string;implementationStatus:'已接入真实组件'|'仅有内部演示'|'尚未实现';props:PropSpec[];events:string[];slots:string[];designSpecs:Record<string,string|number>}
function entry(name:string,title:string,category:string,component:Component,props:string[],example:string,source='ui',variants:string[]=[],sourceName=name):BaseEntry{return{name,title,category,component:markRaw(component),supportedProps:props,variants,description:`复用项目真实 ${name}；本地演示不连接业务服务。`,examples:example,accessibilityNotes:'标签关联真实控件；使用Tab导航，Enter/Space操作。浮层可Escape关闭。实际未检测项标NOT VERIFIED。',sourcePath:`frontend/src/components/${source}/${sourceName==='MarkdownRenderer'?'ChatMarkdownRenderer':sourceName==='CodeBlock'?'ChatCodeBlock':sourceName}.vue`}}
const seeds:BaseEntry[]=[
 entry('Progress','进度条','反馈组件',Progress,['percentage','showLabel'],'<Progress :percentage="64" show-label />'),
 entry('SkeletonList','骨架屏','反馈组件',SkeletonList,['rows','type'],'<SkeletonList :rows="2" type="line" />'),
 entry('AppDialog','对话框','弹窗与浮层',AppDialog,['open','title','scrollable'],'<AppDialog :open="open" title="预览" @cancel="open=false" @confirm="open=false" />'),
 entry('ToastHost','通知','反馈组件',ToastHost,[],"// 复用App全局ToastHost；不重复挂载\nnotify('本地通知', 'success')"),
 entry('Button','按钮','按钮与图标',Button,['size','variant','disabled','loading','iconName','iconPosition','ariaLabel'], '<Button size="md" variant="primary" icon-name="plus">新增</Button>','ui',['primary','secondary','outline','ghost','text','danger']),
 entry('Icon','图标','按钮与图标',Icon,['name','size'],'<Icon name="search" :size="16" />'),
 entry('Input','输入框','表单输入',Input,['modelValue','label','error','disabled','prefixIconName'],'<Input v-model="text" label="名称" prefix-icon-name="search" />'),
 entry('Textarea','多行输入','表单输入',Textarea,['modelValue','label','error','disabled'],'<Textarea v-model="text" label="说明" />'),
 entry('Checkbox','复选框','表单输入',Checkbox,['modelValue','indeterminate','disabled','label'],'<Checkbox v-model="checked" label="接受本地预览" />'),
 {name:'TagsInput',title:'标签输入',category:'表单输入',component:markRaw(KfTagsInput),supportedProps:['modelValue','placeholder','disabled','readonly','maxTags','maxTagLength','allowDuplicates','separatorKeys','size','error','clearable','name','id'],variants:['default','multiple-tags','empty','disabled','readonly','error'],description:'复用项目真实 KfTagsInput；受控标签数组输入，本地预览不连接业务服务。',examples:'<KfTagsInput v-model="tags" placeholder="输入标签后按 Enter" />',accessibilityNotes:'原生 input + button；Enter 添加、空输入 Backspace 删除末尾、删除按钮具备可访问名称；IME 组合输入受保护；容器聚焦不出现可见边框，forced-colors 保留系统焦点。人工读屏与全站集成 NOT VERIFIED。',sourcePath:'frontend/src/components/ui/KfTagsInput.vue'},
 entry('Select','选择器','表单输入',Select,['modelValue','options','searchable','label','disabled','error'],'<Select v-model="choice" label="知识库" :options="options" searchable />'),
 entry('DatePicker','日期','表单输入',DatePicker,['modelValue','label','disabled'],'<DatePicker v-model="date" label="日期" />'),
 entry('DayPicker','日期（Day）','表单输入',DatePicker,['modelValue','label','disabled','readonly','min','max'],'<DatePicker v-model="date" label="具体日期" min="2026-10-01" max="2026-10-31" />','ui',[],'DatePicker'),
 entry('YearPicker','年份','表单输入',YearPicker,['modelValue','label','minYear','maxYear'],'<YearPicker v-model="year" label="年份" :min-year="2020" :max-year="2035" />'),
 entry('MonthPicker','年月','表单输入',MonthPicker,['modelValue','label','minMonth','maxMonth'],'<MonthPicker v-model="month" label="年月" min-month="2026-01" max-month="2027-12" />'),
 entry('TimePicker','时间 · 时分','表单输入',TimePicker,['modelValue','precision','label','min','max'],'<TimePicker v-model="time" label="时间" precision="minute" min="08:00" max="22:00" />'),
 entry('TimePickerSeconds','时间 · 时分秒','表单输入',TimePicker,['modelValue','precision','label','min','max'],'<TimePicker v-model="timeSecond" label="时间（秒）" precision="second" />','ui',[],'TimePicker'),
 entry('DateTimePicker','日期时间 · 分钟','表单输入',DateTimePicker,['modelValue','precision','label','min','max'],'<DateTimePicker v-model="dateTime" label="日期时间" precision="minute" />'),
 entry('DateTimePickerSeconds','日期时间 · 秒','表单输入',DateTimePicker,['modelValue','precision','label','min','max'],'<DateTimePicker v-model="dateTimeSecond" label="日期时间（秒）" precision="second" />','ui',[],'DateTimePicker'),
 entry('DateRangePicker','日期范围','表单输入',DateRangePicker,['modelValue','label','min','max','clearable'],'<DateRangePicker v-model="dateRange" label="日期范围" />'),
 entry('TimeRangePicker','时间范围','表单输入',TimeRangePicker,['modelValue','precision','label','allowOvernight','min','max'],'<TimeRangePicker v-model="timeRange" label="时间范围" />'),
 entry('DateTimeRangePicker','日期时间范围','表单输入',DateTimeRangePicker,['modelValue','precision','label','min','max'],'<DateTimeRangePicker v-model="dateTimeRange" label="日期时间范围" />'),
 entry('FormField','表单轨道','布局组件',FormField,['controlId','label','help','error'],'<FormField control-id="demo" label="字段" help="本地帮助"><input id="demo" /></FormField>'),
 entry('Badge','徽标','数据展示',Badge,['variant'],'<Badge variant="success">已完成</Badge>'),
 entry('Card','卡片','数据展示',Card,['variant','padding'],'<Card variant="flat">卡片内容</Card>'),
 entry('Pagination','分页','导航组件',Pagination,['pageNum','pageSize','total'],'<Pagination :page-num="page" :page-size="10" :total="50" @change="page = $event" />'),
 entry('ConfirmDialog','确认弹窗','弹窗与浮层',ConfirmDialog,['open','title','description','loading','variant'],'<ConfirmDialog :open="open" title="本地确认" @cancel="open=false" @confirm="open=false" />'),
 entry('ContextMenu','上下文菜单','弹窗与浮层',ContextMenu,['open','x','y','items'],'<ContextMenu :open="open" :x="x" :y="y" :items="items" @close="open=false" />'),
 entry('EmptyState','空状态','反馈组件',EmptyState,['title','description'],'<EmptyState title="没有数据" description="本地示例" />'),
 entry('ErrorState','错误状态','反馈组件',ErrorState,['title','message'],'<ErrorState title="加载失败" message="仅演示" />'),
 entry('AIComposer','AI输入区','AI组件',AIComposer,['modelValue','busy','stoppable','error'],'<AIComposer v-model="text" @submit="onLocalSubmit" />','ai'),
 entry('CodeBlock','代码块','AI组件',CodeBlock,['code','lang'],'<ChatCodeBlock code="const ready = true" lang="ts" />','chat'),
 entry('MarkdownRenderer','Markdown正文','AI组件',MarkdownRenderer,['content'],'<ChatMarkdownRenderer content="## 本地文档" />','chat'),
 entry('Radio','单选组','表单输入',Radio,['modelValue','options','label','disabled','readonly','error'],''),
 entry('Switch','开关','表单输入',Switch,['modelValue','label','disabled','readonly','loading'],''),
 entry('Tabs','标签页','导航组件',Tabs,['modelValue','items','label'],''),
 entry('SearchInput','搜索框','表单输入',SearchInput,['modelValue','label','placeholder','disabled','readonly','loading','error'],''),
 ...['Tag','Divider','Typography','Link','Upload','Breadcrumb','Dropdown','Tooltip','Popover','Drawer','Alert','Loading','Skeleton','Flashcard','ReviewProgress','QuestionCard'].map(name=>({name,title:name,category:'待纳入',description:'未注册独立通用组件；现有CSS/业务专用实现不等于公共组件。',supportedProps:[],variants:[],examples:'',accessibilityNotes:'NOT VERIFIED',sourcePath:''})),
]
export const entries:(PlaygroundEntry & {production:ProductionMetadata})[]=[...buildCatalog(seeds),...extensionEntries,...['TechEnglishTerm','NoteEditorDialog'].map(name=>({id:name,name,title:name==='TechEnglishTerm'?'技术英语术语':'笔记编辑弹窗',category:'组合组件',previewKey:name,description:'隔离业务Fixture；数据/API/权限由调用方负责。',supportedProps:[],variants:[],examples:'',accessibilityNotes:'NOT VERIFIED',sourcePath:'frontend/src/dev/ScenarioPreview.vue',implementationStatus:'仅有内部演示' as const,props:[],events:['event'],slots:[],designSpecs:{}}))].map(entry=>{
 if(['TimePickerSeconds','DateTimePickerSeconds'].includes(entry.name)){const precision=entry.props.find(p=>p.name==='precision');if(precision)precision.default='second'}
 if(newControls.includes(entry.name)){
  entry.props=controlProps(entry.name);entry.supportedProps=entry.props.map(p=>p.name)
  entry.events=['update:modelValue',...(entry.name==='SearchInput'?['search','clear']:['change'])]
  entry.slots=entry.name==='Tabs'?['overview','notes']:[]
  entry.accessibilityNotes='真实键盘与ARIA合同见源码/独立测试；人工读屏、对比度和完整认证 NOT VERIFIED。'
 }
 return {...entry,production:productionMetadata(entry)}
})
export function searchEntries(query:string,category='全部'){const q=query.trim().toLowerCase();return entries.filter(e=>(category==='全部'||e.category===category)&&`${e.name} ${e.title} ${e.category} ${e.description} ${e.props.map(p=>p.name).join(' ')} ${Object.keys(e.designSpecs).join(' ')}`.toLowerCase().includes(q))}
// Names only; every value is read from the existing computed CSS, never copied into another theme.
export const tokenGroups:Record<string,string[]>={
 颜色:['--kf-primary','--kf-foreground','--kf-background','--kf-card','--kf-border','--kf-muted','--kf-state-success','--kf-state-warning','--kf-state-error','--kf-danger-text'],
 字体:['--font-sans','--font-mono','--kf-fs-h1','--kf-fs-h2','--kf-fs-h3','--kf-fs-h4','--kf-fs-body-lg','--kf-fs-body-md','--kf-fs-body-sm','--kf-fs-caption','--kf-lh-body-md','--kf-lh-h1','--kf-fw-label','--kf-fw-h1'],
 尺寸:['--kf-btn-height-sm','--kf-btn-height-md','--kf-btn-height-lg','--kf-input-height','--kf-checkbox-size','--kf-button-icon-slot'],
 间距:['--kf-space-1','--kf-space-2','--kf-space-3','--kf-space-4','--kf-space-5','--kf-space-6','--kf-space-8'],
 圆角:['--kf-radius-xs','--kf-radius-sm','--kf-radius-md','--kf-radius-lg','--kf-radius-xl','--kf-radius-full'],
 阴影:['--shadow-dropdown-2','--shadow-dropdown-3','--kf-shadow-md']}
