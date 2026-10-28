<template>
  <section class="graph-explorer" :aria-label="title">
    <GraphToolbar label="图谱画布操作">
      <template v-if="searchable" #search><div class="kf-field"><input v-model="search" type="search" class="kf-input kf-form-control" aria-label="搜索图谱节点" placeholder="搜索全部节点名称…" @keydown.enter="searchFocus" /></div></template>
      <template #filters><Select v-model="scope" aria-label="节点关联筛选" :options="scopeOptions" variant="pill" :panel-width="240" /><Select v-model="type" aria-label="节点类型筛选" :options="typeOptions" variant="pill" :panel-width="240" /></template>
      <template #actions><Button class="graph-icon-action" variant="secondary" icon-name="zoom-out" icon-only aria-label="缩小图谱" title="缩小图谱（−）" :disabled="!scene.nodes.length || camera.scale <= 0.05" @click="zoomAt(0.8)" /><output aria-label="画布缩放">{{ Math.round(camera.scale * 100) }}%</output><Button class="graph-icon-action" variant="secondary" icon-name="zoom-in" icon-only aria-label="放大图谱" title="放大图谱（+）" :disabled="!scene.nodes.length || camera.scale >= 2.5" @click="zoomAt(1.25)" /><Button variant="secondary" icon-name="maximize" aria-label="适应画布" title="显示当前子图全部节点与连线" :disabled="!scene.nodes.length" @click="schedule('fit')">适应画布</Button><Button variant="secondary" icon-name="rotate-ccw" aria-label="重置视图" title="恢复默认可读密度并取消选择" :disabled="!scene.nodes.length" @click="resetView">重置</Button></template>
    </GraphToolbar>
    <p class="graph-count" role="status" aria-live="polite">显示 {{ scene.nodes.length }} / {{ nodes.length }} 个节点 · {{ scene.edges.length }} / {{ edges.length }} 条关系 · 孤立 {{ inspection.isolated }} 个<span v-if="searchText"> · 名称匹配 {{ scene.matches }} 个</span></p>
    <div ref="viewport" class="graph-viewport" tabindex="0" aria-label="图谱画布，可拖拽平移，滚轮缩放" @wheel="wheel" @pointerdown="startPan" @pointermove="movePan" @pointerup="endPan" @pointercancel="endPan" @keydown="canvasKey">
      <svg width="100%" height="100%" :viewBox="`0 0 ${size.width || 800} ${size.height || 520}`" :aria-label="title" role="group">
        <defs><marker :id="markerId" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="var(--kf-muted-foreground)" /></marker></defs>
        <g class="graph-camera" :transform="`translate(${camera.x},${camera.y}) scale(${camera.scale})`">
          <g v-for="(edge,i) in paths" :key="i" class="graph-edge" :opacity="selectedId && edge.source !== selectedId && edge.target !== selectedId ? 0.12 : selectedId ? 0.9 : 0.5">
            <path :d="edge.path" fill="none" :stroke="selectedId && (edge.source === selectedId || edge.target === selectedId) ? 'var(--kf-primary)' : 'var(--kf-muted-foreground)'" :stroke-dasharray="edge.relation === 'COMPONENT' ? '5 4' : undefined" :stroke-width="selectedId && (edge.source === selectedId || edge.target === selectedId) ? 2 : 1.2" :marker-end="edge.directed ? `url(#${markerId})` : undefined" />
            <title>{{ edge.label || edge.relation }}</title>

          </g>
          <g v-for="label in edgeLabels" :key="label.index" class="graph-edge-label" pointer-events="none"><rect :x="label.x-label.width/2" :y="label.y-label.height/2" :width="label.width" :height="label.height" rx="4" fill="var(--kf-card)" stroke="var(--kf-border)" /><text :x="label.x" :y="label.y" dominant-baseline="central" text-anchor="middle" class="edge-label">{{ label.displayText }}</text><title>{{ label.text }}</title></g>
          <g v-for="node in scene.positioned" :key="node.id" class="graph-node" role="button" tabindex="0" :aria-label="node.label" :aria-pressed="selectedId === node.id" :opacity="selectedId && selectedId !== node.id && !inspection.neighbors.get(selectedId)?.has(node.id) ? 0.3 : 1" @click.stop="choose(node.id)" @keydown.enter.stop.prevent="choose(node.id)" @keydown.space.stop.prevent="choose(node.id)">
            <title>{{ node.label }} · {{ node.typeLabel || node.type }}</title>
            <rect :x="node.x-node.width/2" :y="node.y-node.height/2" :width="node.width" :height="node.height" rx="10" :fill="selectedId === node.id ? 'var(--kf-primary-soft)' : 'var(--kf-card)'" :stroke="selectedId === node.id ? 'var(--kf-primary)' : 'var(--kf-border)'" :stroke-width="selectedId === node.id ? 1.75 : 1.25" />
            <rect :x="node.x-node.width/2+10" :y="node.y-node.height/2+12" width="3" height="48" rx="1.5" :fill="node.color || 'var(--kf-primary)'" />
            <text class="node-label" :x="node.x" :y="node.y-12" text-anchor="middle"><tspan v-for="(line,i) in graphLabelLines(node.label)" :key="i" :x="node.x" :dy="i ? 18 : 0">{{ line }}</tspan></text>
            <text class="node-type" :x="node.x-node.width/2+22" :y="node.y+27">{{ node.typeLabel || node.type }} · {{ inspection.degree.get(node.id) || 0 }} 条关系</text>
            <text v-if="node.annotation" class="node-type" :x="node.x+node.width/2-12" :y="node.y+27" text-anchor="end">{{ node.annotation }}</text>
          </g>
        </g>
      </svg>
      <p v-if="!scene.nodes.length" class="graph-empty">没有匹配的图谱节点，请调整搜索或筛选。</p>
    </div>
    <div class="graph-footer"><span>{{ mode === 'relations' ? '仅展示真实关系子图；更多节点可平移探索，孤立节点可单独检索。' : mode === 'hierarchy' ? '按真实分类层级排列，展示过滤不删除数据。' : '按真实依赖方向排列，不推断额外父子关系。' }}</span><Button v-if="scene.nodes.length < scene.available" variant="secondary" title="增量显示更多真实节点，保留缩放比例" @click="more">显示更多节点</Button><Button v-if="selectedId" variant="secondary" title="展开当前节点全部直接关联" @click="expand">展开一跳关联</Button><Button v-if="selectedId" variant="secondary" title="以可读比例居中选中节点" @click="schedule('focus')">聚焦选中节点</Button><Button v-if="selectedId" variant="secondary" title="取消选择（Escape）" @click="emit('select',null)">取消选择</Button></div>
    <details v-if="selectedId && selectedRelations.length" open class="graph-relation-list"><summary>全部直接关系（{{ selectedRelations.length }}）<span v-if="edgeLabels.length < selectedRelations.length"> · 拥挤标签在此完整显示</span></summary><ul><li v-for="(edge,i) in selectedRelations" :key="i"><span>{{ inspection.byId.get(edge.source)?.label }}</span><strong>{{ edge.directed ? '→' : '—' }} {{ edge.label || edge.relation }}</strong><span>{{ inspection.byId.get(edge.target)?.label }}</span></li></ul></details>
    <p v-if="paths.some(edge=>edge.obstructed)" class="graph-count">部分连线无法避开当前重叠坐标，请重置视图或减少展示范围；真实关系仍保留。</p>
    <p v-if="inspection.invalidEdges || inspection.duplicateIds || inspection.duplicateEdges || inspection.selfLoops" class="graph-count">数据提示：{{ inspection.invalidEdges }} 条无效端点、{{ inspection.duplicateIds }} 个重复ID、{{ inspection.duplicateEdges }} 条重复关系、{{ inspection.selfLoops }} 个自环；仅清理展示索引，不改原数据。</p>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import Button from '@/components/ui/Button.vue'
import Select from '@/components/ui/Select.vue'
import GraphToolbar from './GraphToolbar.vue'
import { routeGraphEdges, routedGraphBounds, placeGraphEdgeLabels } from '@/utils/knowledgeGraphRouting'
import { fitGraph, graphLabelLines, inspectGraph, layoutKnowledgeGraph, selectGraphView, type CanvasGraphNode, type CanvasGraphEdge, type GraphLayoutMode } from '@/utils/knowledgeGraphLayout'
const props = withDefaults(defineProps<{nodes:CanvasGraphNode[];edges:CanvasGraphEdge[];mode?:GraphLayoutMode;selectedId?:string|null;keyword?:string;searchable?:boolean;title?:string}>(), {mode:'relations',selectedId:null,keyword:'',searchable:true,title:'知识关系画布'})
const emit=defineEmits<{select:[id:string|null]}>()
const markerId='graph-arrow-'+useId().replace(/[^a-z0-9-]/gi,'')
const viewport=ref<HTMLElement|null>(null), size=ref({width:0,height:0}), camera=ref({x:0,y:0,scale:1})
const search=ref(''),type=ref(''),scope=ref<'connected'|'isolated'|'all'>(props.mode==='relations'?'connected':'all'),limit=ref(0),expanded=ref<string[]>([])
const searchText=computed(()=>props.keyword || search.value)
const inspection=computed(()=>inspectGraph(props.nodes,props.edges))
const types=computed(()=>[...new Map(props.nodes.map(n=>[n.type,n.typeLabel||n.type])).entries()])
const scopeOptions=[{value:'connected',label:'有真实关系'},{value:'isolated',label:'孤立节点'},{value:'all',label:'全部节点'}]
const typeOptions=computed(()=>[{value:'',label:'全部类型'},...types.value.map(([value,label])=>({value,label}))])
const capacity=computed(()=>Math.max(3,Math.floor((size.value.width||900)/224)*Math.floor((size.value.height||520)/156)))
const scene=computed(()=>{
  const start=performance.now();let count=limit.value || capacity.value
  let view=selectGraphView(props.nodes,props.edges,{limit:count,keyword:searchText.value,type:type.value,scope:scope.value,selectedId:props.selectedId,expanded:expanded.value,mode:props.mode})
  let positioned=layoutKnowledgeGraph(view.nodes,view.edges,props.mode,Math.max(224,size.value.width-48),Math.max(224,size.value.height-48))
  let routes=routeGraphEdges(positioned,view.edges,props.mode)
  // 默认视图依据真实拓扑边界与容器收敛密度，避免“适应全部”把14px标签缩成不可读小字。
  while(!limit.value && count>3 && (fitGraph(routedGraphBounds(positioned,routes),size.value.width,size.value.height)?.scale ?? 1)<12/14) {
    count=Math.max(3,Math.floor(count*0.75));view=selectGraphView(props.nodes,props.edges,{limit:count,keyword:searchText.value,type:type.value,scope:scope.value,selectedId:props.selectedId,expanded:expanded.value,mode:props.mode});positioned=layoutKnowledgeGraph(view.nodes,view.edges,props.mode,Math.max(224,size.value.width-48),Math.max(224,size.value.height-48));routes=routeGraphEdges(positioned,view.edges,props.mode)
  }
  return {...view,positioned,routes,layoutMs:performance.now()-start}
})
const paths=computed(()=>scene.value.routes)
const edgeLabels=computed(()=>placeGraphEdgeLabels(paths.value,scene.value.positioned,props.selectedId))
const selectedRelations=computed(()=>inspection.value.edges.filter(edge=>edge.source===props.selectedId||edge.target===props.selectedId))
let observer:ResizeObserver|undefined, frame=0, stopped=false, intent:'fit'|'focus'='fit'
function schedule(next:'fit'|'focus'='fit') {
  if(stopped)return
  intent=next;cancelAnimationFrame(frame)
  frame=requestAnimationFrame(()=>{
    if(stopped||!viewport.value)return
    const width=viewport.value.clientWidth,height=viewport.value.clientHeight
    if(!width||!height)return // 隐藏Tab不能使用零尺寸；等可见后的ResizeObserver再适配。
    if(size.value.width!==width||size.value.height!==height){size.value={width,height};void nextTick(()=>schedule(intent));return}
    const fitted=fitGraph(routedGraphBounds(scene.value.positioned,paths.value,edgeLabels.value),width,height)
    if(!fitted)return
    const node=intent==='focus'?scene.value.positioned.find(n=>n.id===props.selectedId):undefined
    camera.value=node?{scale:1,x:width/2-node.x,y:height/2-node.y}:fitted
  })
}
function zoomAt(factor:number,point={x:size.value.width/2,y:size.value.height/2}){const old=camera.value.scale,scale=Math.min(2.5,Math.max(0.05,old*factor));camera.value={scale,x:point.x-(point.x-camera.value.x)*scale/old,y:point.y-(point.y-camera.value.y)*scale/old}}
function wheel(e:WheelEvent){if(e.ctrlKey)return;e.preventDefault();const bounds=viewport.value!.getBoundingClientRect();zoomAt(e.deltaY>0?0.9:1.1,{x:e.clientX-bounds.left,y:e.clientY-bounds.top})}
let drag:{id:number;x:number;y:number;left:number;top:number}|null=null
function startPan(e:PointerEvent){if(e.button!==0||(e.target as Element).closest('.graph-node'))return;drag={id:e.pointerId,x:e.clientX,y:e.clientY,left:camera.value.x,top:camera.value.y};viewport.value?.setPointerCapture?.(e.pointerId)}
function movePan(e:PointerEvent){if(drag?.id===e.pointerId)camera.value={...camera.value,x:drag.left+e.clientX-drag.x,y:drag.top+e.clientY-drag.y}}
function endPan(){drag=null}
function choose(id:string){emit('select',props.selectedId===id?null:id)}
function searchFocus(e:KeyboardEvent){if(e.isComposing||e.keyCode===229)return;const node=scene.value.nodes.find(n=>n.label.toLocaleLowerCase().includes(searchText.value.trim().toLocaleLowerCase()));if(node)emit('select',node.id)}
// 增量加载保留当前阅读比例，新增内容通过平移探索；适应画布仍可主动查看全貌。
let preserveAfterMore=false, fitAfterExpand=false
function resetView(){limit.value=0;expanded.value=[];emit('select',null);schedule('fit')}
function more(){preserveAfterMore=true;limit.value=Math.max(scene.value.nodes.length,limit.value)+capacity.value}
function expand(){if(!props.selectedId)return;fitAfterExpand=true;expanded.value=[...new Set([...expanded.value,...inspection.value.neighbors.get(props.selectedId)||[]])];limit.value=Math.max(capacity.value,scene.value.nodes.length+expanded.value.length);schedule('fit')}
function canvasKey(e:KeyboardEvent){if(e.key==='Escape'){emit('select',null);return}if((e.target as Element).closest('.graph-node'))return;if(e.key==='+'||e.key==='='){e.preventDefault();zoomAt(1.25)}else if(e.key==='-'){e.preventDefault();zoomAt(0.8)}else if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)){e.preventDefault();camera.value={...camera.value,x:camera.value.x+(e.key==='ArrowLeft'?30:e.key==='ArrowRight'?-30:0),y:camera.value.y+(e.key==='ArrowUp'?30:e.key==='ArrowDown'?-30:0)}}}
watch([searchText,type,scope],()=>{limit.value=0;expanded.value=[];emit('select',null)})
watch([()=>scene.value.positioned,()=>props.selectedId],()=>{if(preserveAfterMore){preserveAfterMore=false;return}if(fitAfterExpand){fitAfterExpand=false;schedule('fit');return}schedule(props.selectedId?'focus':'fit')},{flush:'post'})
onMounted(()=>{if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(()=>schedule('fit'));if(viewport.value)observer.observe(viewport.value)}schedule()})
onBeforeUnmount(()=>{stopped=true;cancelAnimationFrame(frame);observer?.disconnect();drag=null})
defineExpose({schedule,scene,camera,size})
</script>
<style scoped>
.graph-explorer { min-width:0; }
.graph-count { margin-block:var(--kf-space-3); color:var(--kf-muted-foreground); font-size:var(--kf-fs-caption); }
.graph-viewport { position:relative; width:100%; height:clamp(22rem,50dvh,36rem); min-width:0; overflow:hidden; border:1px solid var(--kf-border); border-radius:var(--kf-radius-md); background:var(--kf-background); touch-action:none; user-select:none; cursor:grab; }
.graph-viewport:active { cursor:grabbing; }
.graph-viewport svg { display:block; }
.node-label { fill:var(--kf-foreground); font-size:var(--kf-fs-body-md); font-weight:600; }
.node-type,.edge-label { fill:var(--kf-muted-foreground); font-size:var(--kf-fs-xs); }
.graph-node { cursor:pointer; }
.graph-node:focus { outline:none; }
.graph-node:focus-visible rect:first-of-type { stroke:var(--kf-ring); stroke-width:3; }
.graph-footer { display:flex; flex-wrap:wrap; align-items:center; gap:var(--kf-space-2); margin-top:var(--kf-space-3); font-size:var(--kf-fs-caption); color:var(--kf-muted-foreground); }
.graph-footer > span { flex:1; min-width:12rem; }
.graph-footer :deep(button:focus-visible),.graph-viewport:focus-visible { box-shadow:none; outline:2px solid var(--kf-ring); outline-offset:2px; }
.graph-empty { position:absolute; inset:40% var(--kf-space-4) auto; text-align:center; color:var(--kf-muted-foreground); }
.graph-relation-list { margin-top:var(--kf-space-3); font-size:var(--kf-fs-body-sm); }
.graph-relation-list summary { cursor:pointer; color:var(--kf-muted-foreground); }
.graph-relation-list ul { max-height:12rem; overflow:auto; list-style:none; padding:0; margin-block:var(--kf-space-2); }
.graph-relation-list li { display:flex; gap:var(--kf-space-2); flex-wrap:wrap; padding-block:var(--kf-space-2); border-bottom:1px solid var(--kf-border); overflow-wrap:anywhere; }
.graph-relation-list strong { font-weight:500; color:var(--kf-muted-foreground); }
</style>
