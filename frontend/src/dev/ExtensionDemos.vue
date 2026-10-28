<script setup lang="ts">
import {computed,ref} from 'vue'
import Avatar from '@/components/ui/Avatar.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
const props=withDefaults(defineProps<{name:string;settings?:Record<string,unknown>}>(),{settings:()=>({})})
const emit=defineEmits<{event:[name:string,value:unknown]}>()
const avatarSize=computed(()=>['sm','md','lg','xl'].includes(String(props.settings.size))?props.settings.size as 'sm'|'md'|'lg'|'xl':'md')
const avatarStatus=computed(()=>['online','offline','busy','away'].includes(String(props.settings.status))?props.settings.status as 'online'|'offline'|'busy'|'away':'online')
const record=(name:string,value:unknown)=>emit('event',name,value)
const view=ref('列表'),volume=ref(40),suffix=ref('notes'),number=ref(''),copyText=ref('KnowFlow 本地示例'),copyState=ref(''),status=ref('online'),split=ref(50),bold=ref(false),italic=ref(false)
const query=ref(''),ascending=ref(true),page=ref(1),showCategory=ref(true),selected=ref<string[]>([])
const rows=[{id:'1',title:'Evidence 聚合',category:'架构'},{id:'2',title:'Vue 组合式 API',category:'前端'},{id:'3',title:'知识卡片',category:'学习'},{id:'4',title:'设计 Token',category:'设计'},{id:'5',title:'键盘导航',category:'设计'},{id:'6',title:'本地预览',category:'前端'}]
const filtered=computed(()=>rows.filter(row=>row.title.toLowerCase().includes(query.value.toLowerCase())).sort((a,b)=>(ascending.value?1:-1)*a.title.localeCompare(b.title,'zh')))
const totalPages=computed(()=>Math.max(1,Math.ceil(filtered.value.length/3)))
const visible=computed(()=>filtered.value.slice((page.value-1)*3,page.value*3))
function normalizeNumber(){if(number.value===''){record('change',null);return}const n=Number(number.value);if(!Number.isFinite(n))return;number.value=String(Math.min(100,Math.max(0,Math.round(n/5)*5)));record('change',Number(number.value))}
async function copy(){try{await navigator.clipboard.writeText(copyText.value);copyState.value='已复制';record('copy',copyText.value)}catch{copyState.value='复制失败：浏览器未允许剪贴板访问';record('copy-error',copyState.value)}}
function resize(event:KeyboardEvent){const delta=event.key==='ArrowLeft'?-5:event.key==='ArrowRight'?5:0;if(delta){event.preventDefault();split.value=Math.min(75,Math.max(25,split.value+delta));record('resize',split.value)}}
</script>
<template>
 <div class="pg5-ext" :data-extension="props.name">
  <div v-if="name==='Accordion'" class="pg5-ext-stack"><details v-for="chapter in ['组件使用','键盘交互','限制说明']" :key="chapter" @toggle="record('toggle',{chapter,open:($event.target as HTMLDetailsElement).open})"><summary>{{chapter}}</summary><p>{{chapter}}：这是独立本地演示。可同时展开多个面板，使用 Tab 和 Enter 操作。</p></details></div>
  <div v-else-if="name==='Avatar'" class="pg5-ext-row"><Avatar :name="String(settings.name||'KnowFlow')" :icon-name="String(settings.iconName??'')" :size="avatarSize" :show-status="settings.showStatus!==false" :status="avatarStatus"/><div v-for="size in (['sm','md','lg','xl'] as const)" :key="size" class="pg5-ext-stack"><Avatar name="KnowFlow" :icon-name="''" :size="size" show-status status="online"/><span>{{size}} · 在线</span></div><p>真实 Avatar；图片错误处理尚无有效回退，不以本演示声明已支持。</p></div>
  <div v-else-if="name==='ButtonGroup'" class="pg5-ext-stack"><div role="group" aria-label="视图" class="pg5-ext-row"><Button v-for="mode in ['列表','网格','时间线']" :key="mode" :variant="view===mode?'primary':'secondary'" :aria-pressed="view===mode" @click="view=mode;record('change',mode)">{{mode}}</Button></div><output>当前视图：{{view}}</output></div>
  <div v-else-if="name==='Slider'" class="pg5-ext-stack"><label for="pg5-ext-slider">音量 {{volume}}%</label><input id="pg5-ext-slider" v-model.number="volume" type="range" min="0" max="100" step="5" @input="record('change',volume)"><input aria-label="禁用滑块" type="range" disabled value="60"></div>
  <div v-else-if="name==='DataTable'" class="pg5-ext-stack"><Input v-model="query" label="筛选标题" @update:model-value="page=1;record('filter',$event)"/><Checkbox v-model="showCategory" label="显示分类"/><div class="pg5-ext-scroll"><table><caption>本地知识条目，共 {{filtered.length}} 条，已选 {{selected.length}} 条</caption><thead><tr><th scope="col">选择</th><th scope="col" :aria-sort="ascending?'ascending':'descending'"><Button variant="text" @click="ascending=!ascending;record('sort',ascending)">标题 {{ascending?'↑':'↓'}}</Button></th><th v-if="showCategory" scope="col">分类</th></tr></thead><tbody><tr v-for="row in visible" :key="row.id"><td><Checkbox v-model="selected" :value="row.id" :label="`选择 ${row.title}`" @change="record('selection',selected)"/></td><td>{{row.title}}</td><td v-if="showCategory">{{row.category}}</td></tr><tr v-if="!visible.length"><td :colspan="showCategory?3:2">没有匹配条目</td></tr></tbody></table></div><div class="pg5-ext-row"><Button :disabled="page<=1" @click="page--;record('page',page)">上一页</Button><span>{{page}} / {{totalPages}}</span><Button :disabled="page>=totalPages" @click="page++;record('page',page)">下一页</Button></div><p>仅六行本地数据；无服务端查询、虚拟滚动或批量业务写入。</p></div>
  <div v-else-if="name==='InputGroup'" class="pg5-ext-stack"><label for="pg5-ext-group">知识空间地址</label><div class="pg5-ext-input-group"><span>knowflow /</span><input id="pg5-ext-group" v-model="suffix" @input="record('change',suffix)"><span>.local</span></div><output>knowflow / {{suffix}}.local</output></div>
  <div v-else-if="name==='NumberInput'" class="pg5-ext-stack"><label for="pg5-ext-number">复习数量（0–100，步进 5）</label><input id="pg5-ext-number" v-model="number" class="kf-input" type="number" min="0" max="100" step="5" @change="normalizeNumber"><output>值：{{number===''?'空值':number}}</output><p>提交变化时吸附到步进并限制范围；空值不会转换为 0。</p></div>
  <div v-else-if="name==='CopyButton'" class="pg5-ext-stack"><Input v-model="copyText" label="待复制文本"/><Button @click="copy">复制文本</Button><p role="status">{{copyState||'点击后请求浏览器剪贴板权限'}}</p></div>
  <div v-else-if="name==='StatusIndicator'" class="pg5-ext-stack"><div class="pg5-ext-row"><Button v-for="state in ['online','busy','away','offline']" :key="state" variant="secondary" :aria-pressed="status===state" @click="status=state;record('change',state)">{{state}}</Button></div><p role="status"><span class="pg5-ext-dot" :class="`pg5-ext-${status}`" aria-hidden="true"/> 当前状态：{{status}}</p></div>
  <div v-else-if="name==='ResizablePanels'" class="pg5-ext-stack"><label for="pg5-ext-split">左侧宽度 {{split}}%</label><input id="pg5-ext-split" v-model.number="split" type="range" min="25" max="75" step="5" @input="record('resize',split)"><div class="pg5-ext-panels"><section :style="{flexBasis:`${split}%`}">本地目录</section><div role="separator" tabindex="0" aria-label="调整左右面板" aria-orientation="vertical" :aria-valuenow="split" :aria-valuemin="25" :aria-valuemax="75" @keydown="resize"/><section>本地预览内容</section></div><p>方向键或上方滑块调整；不支持拖拽分隔线或跨页保存。</p></div>
  <div v-else-if="name==='Toolbar'" class="pg5-ext-stack"><div role="group" aria-label="文本格式工具" class="pg5-ext-row"><Button :aria-pressed="bold" variant="secondary" @click="bold=!bold;record('bold',bold)">粗体</Button><Button :aria-pressed="italic" variant="secondary" @click="italic=!italic;record('italic',italic)">斜体</Button><Button variant="text" :disabled="!bold&&!italic" @click="bold=false;italic=false;record('reset',true)">重置</Button></div><p :class="{'pg5-ext-bold':bold,'pg5-ext-italic':italic}">本地文本格式预览</p><p>格式按钮组，使用 Tab 导航；不声明支持富文本编辑器命令或撤销历史。</p></div>
 </div>
</template>
<style scoped>
.pg5-ext{width:100%;color:var(--kf-foreground);font-size:var(--kf-fs-body-md)}
.pg5-ext-stack{display:flex;flex-direction:column;gap:var(--kf-space-3)}
.pg5-ext-row{display:flex;align-items:center;flex-wrap:wrap;gap:var(--kf-space-2)}
.pg5-ext details,.pg5-ext-panels section{padding:var(--kf-space-3);background:var(--kf-muted);border-radius:var(--kf-radius-sm)}
.pg5-ext summary{cursor:pointer}.pg5-ext p{margin:0}.pg5-ext-scroll{overflow:auto}.pg5-ext table{width:100%;border-collapse:collapse;text-align:left}.pg5-ext th,.pg5-ext td{padding:var(--kf-space-2);border-bottom:1px solid var(--kf-border)}.pg5-ext caption{text-align:left;padding:var(--kf-space-2)}
.pg5-ext-input-group{display:flex;align-items:center;gap:var(--kf-space-2);padding-inline:var(--kf-space-3);background:var(--kf-field-bg,var(--kf-muted));border-radius:var(--kf-radius-sm)}.pg5-ext-input-group input{min-width:0;flex:1;background:transparent;border:0;outline:none;box-shadow:none;height:var(--kf-input-height)}
.pg5-ext-dot{display:inline-block;width:var(--kf-space-2);height:var(--kf-space-2);border-radius:var(--kf-radius-full);background:var(--kf-muted-foreground)}.pg5-ext-online{background:var(--kf-state-success)}.pg5-ext-busy{background:var(--kf-state-error)}.pg5-ext-away{background:var(--kf-state-warning)}
.pg5-ext-panels{display:flex;min-height:var(--kf-space-8);gap:var(--kf-space-2)}.pg5-ext-panels section{min-width:0;overflow-wrap:anywhere;flex-grow:1}.pg5-ext-panels [role=separator]{width:var(--kf-space-2);flex-shrink:0;background:var(--kf-border);cursor:col-resize}.pg5-ext-bold{font-weight:var(--kf-fw-bold,700)}.pg5-ext-italic{font-style:italic}
@media(forced-colors:active){.pg5-ext-input-group input:focus{outline:1px solid Highlight}.pg5-ext-dot{forced-color-adjust:none}}
</style>
