import dagre from '@dagrejs/dagre'

export interface CanvasGraphNode { id: string; label: string; type: string; typeLabel?: string; color?: string; annotation?: string }
export interface CanvasGraphEdge { source: string; target: string; relation: string; label?: string; directed?: boolean }
export interface PositionedGraphNode extends CanvasGraphNode { x: number; y: number; width: number; height: number }
export type GraphLayoutMode = 'hierarchy' | 'relations' | 'dependency'
export const GRAPH_NODE_WIDTH = 184
export const GRAPH_NODE_HEIGHT = 76

/** 数据只作展示索引，不合并同名节点、不回写服务器，不凭空补边。重复ID只显示首项并明确告警。 */
export function inspectGraph(nodes: CanvasGraphNode[], edges: CanvasGraphEdge[]) {
  const byId = new Map<string, CanvasGraphNode>()
  let duplicateIds = 0, duplicateEdges = 0, invalidEdges = 0, selfLoops = 0
  for (const n of nodes) { if (byId.has(n.id)) duplicateIds++; else byId.set(n.id, n) }
  const neighbors = new Map([...byId.keys()].map(id => [id, new Set<string>()]))
  const degree = new Map([...byId.keys()].map(id => [id, 0]))
  const keys = new Set<string>()
  const validEdges = edges.filter(e => {
    const key = JSON.stringify([e.source, e.target, e.relation])
    if (keys.has(key)) duplicateEdges++
    keys.add(key)
    if (!byId.has(e.source) || !byId.has(e.target)) { invalidEdges++; return false }
    if (e.source === e.target) selfLoops++
    neighbors.get(e.source)!.add(e.target); neighbors.get(e.target)!.add(e.source)
    degree.set(e.source, degree.get(e.source)! + 1)
    if (e.target !== e.source) degree.set(e.target, degree.get(e.target)! + 1)
    return true
  })
  return { nodes: [...byId.values()], edges: validEdges, byId, degree, neighbors, duplicateIds, duplicateEdges, invalidEdges, selfLoops, isolated: [...degree.values()].filter(n => n === 0).length }
}

export interface GraphViewOptions { limit: number; keyword?: string; type?: string; scope?: 'connected'|'isolated'|'all'; selectedId?: string|null; expanded?: string[]; mode?: GraphLayoutMode }
export function selectGraphView(nodes: CanvasGraphNode[], edges: CanvasGraphEdge[], options: GraphViewOptions) {
  const graph = inspectGraph(nodes, edges), query = (options.keyword || '').trim().toLocaleLowerCase()
  const typed = graph.nodes.filter(n => !options.type || n.type === options.type)
  const typedIds = new Set(typed.map(n => n.id))
  // 搜索覆盖孤立节点，不让默认“有关系”视图使真实知识点变得不可检索。
  const matches = query ? typed.filter(n => n.label.toLocaleLowerCase().includes(query)) : []
  const matchIds = new Set(matches.map(n => n.id))
  let pool = typed.filter(n => query ? matchIds.has(n.id) : options.scope === 'isolated' ? !graph.degree.get(n.id) : options.scope === 'connected' && graph.isolated < graph.nodes.length ? !!graph.degree.get(n.id) : true)
  if (query) {
    const context = new Set(matchIds)
    for (const id of matchIds) for (const nb of graph.neighbors.get(id) || []) if (typedIds.has(nb)) context.add(nb)
    pool = typed.filter(n => context.has(n.id))
  }
  const poolIds = new Set(pool.map(n => n.id))
  const order = (a: CanvasGraphNode, b: CanvasGraphNode) => Number(matchIds.has(b.id)) - Number(matchIds.has(a.id)) || (options.mode === 'hierarchy' ? Number(b.type === 'category') - Number(a.type === 'category') : 0) || graph.degree.get(b.id)! - graph.degree.get(a.id)! || a.id.localeCompare(b.id, undefined, { numeric: true })
  pool = [...pool].sort(order)
  const selected = new Set<string>()
  if (options.selectedId && typedIds.has(options.selectedId)) selected.add(options.selectedId)
  for (const id of options.expanded || []) if (typedIds.has(id)) selected.add(id)
  const limit = Math.max(1, options.limit)
  // 层级视图优先显示真实分类骨架；实体视图沿真实邻接遍历连通子图。
  if (options.mode === 'hierarchy') { for (const n of pool) { if (selected.size >= limit) break; selected.add(n.id) } }
  else {
    for (const seed of pool) {
      if (selected.size >= limit) break
      const queue = [seed.id], seen = new Set<string>()
      while (queue.length && selected.size < limit) {
        const id = queue.shift()!
        if (seen.has(id) || !poolIds.has(id)) continue
        seen.add(id); selected.add(id)
        const next = [...graph.neighbors.get(id) || []].filter(x => !seen.has(x) && poolIds.has(x))
        queue.push(...next)
      }
    }
  }
  return { nodes: graph.nodes.filter(n => selected.has(n.id)), edges: graph.edges.filter(e => selected.has(e.source) && selected.has(e.target)), available: pool.length, matches: matches.length, isolated: graph.isolated, graph }
}

export function graphBounds(nodes: PositionedGraphNode[]) {
  if (!nodes.length) return { x: 0, y: 0, width: 0, height: 0 }
  const x = Math.min(...nodes.map(n => n.x - n.width / 2)), y = Math.min(...nodes.map(n => n.y - n.height / 2))
  return { x, y, width: Math.max(...nodes.map(n => n.x + n.width / 2)) - x, height: Math.max(...nodes.map(n => n.y + n.height / 2)) - y }
}
/** 自环曲线高于节点；适应画布时也必须纳入真实绘制边界，防止上沿被裁切。 */
export function graphDrawingBounds(nodes: PositionedGraphNode[], edges: CanvasGraphEdge[]) {
  const bounds=graphBounds(nodes)
  return edges.some(e=>e.source===e.target) ? {...bounds,y:bounds.y-80,height:bounds.height+80} : bounds
}
export function fitGraph(bounds: ReturnType<typeof graphBounds>, width: number, height: number) {
  if (width <= 0 || height <= 0) return null
  if (!bounds.width || !bounds.height) return { x: width / 2, y: height / 2, scale: 1 }
  const scale = Math.min(1, Math.max(0.01, Math.min(Math.max(1, width - 48) / bounds.width, Math.max(1, height - 48) / bounds.height)))
  return { scale, x: width / 2 - (bounds.x + bounds.width / 2) * scale, y: height / 2 - (bounds.y + bounds.height / 2) * scale }
}

/** 固定迭代的确定性力布局，矩形碰撞计入标题范围；无随机种子、无持续动画或后台实例。 */
function relationLayout(nodes: CanvasGraphNode[], edges: CanvasGraphEdge[], width: number, height: number): PositionedGraphNode[] {
  const index = inspectGraph(nodes, edges), visited = new Set<string>(), groups: PositionedGraphNode[][] = []
  for (const seed of index.nodes) {
    if (visited.has(seed.id)) continue
    const pending = [seed.id], ids: string[] = []
    while (pending.length) { const id = pending.pop()!; if (visited.has(id)) continue; visited.add(id); ids.push(id); pending.push(...index.neighbors.get(id) || []) }
    const cols = Math.ceil(Math.sqrt(ids.length))
    const group = ids.map((id, i) => ({ ...index.byId.get(id)!, x: (i % cols) * 240, y: Math.floor(i / cols) * 130, width: GRAPH_NODE_WIDTH, height: GRAPH_NODE_HEIGHT }))
    const map = new Map(group.map(n => [n.id, n]))
    for (let step = 0; step < 96; step++) {
      for (let i = 0; i < group.length; i++) for (let j = i + 1; j < group.length; j++) {
        const a = group[i]!, b = group[j]!, dx = b.x - a.x || 0.1, dy = b.y - a.y || 0.1
        const distance = Math.max(1, Math.hypot(dx, dy)), push = Math.min(8, 2000 / (distance * distance))
        a.x -= dx / distance * push; a.y -= dy / distance * push; b.x += dx / distance * push; b.y += dy / distance * push
        const ox = GRAPH_NODE_WIDTH + 30 - Math.abs(dx), oy = GRAPH_NODE_HEIGHT + 30 - Math.abs(dy)
        if (ox > 0 && oy > 0) { if (ox < oy) { const move = Math.sign(dx) * (ox / 2 + 1); a.x -= move; b.x += move } else { const move = Math.sign(dy) * (oy / 2 + 1); a.y -= move; b.y += move } }
      }
      for (const e of edges) {
        const a = map.get(e.source), b = map.get(e.target)
        if (!a || !b || a === b) continue
        const dx = b.x - a.x, dy = b.y - a.y, distance = Math.max(1, Math.hypot(dx, dy)), pull = Math.max(-2, Math.min(2, (distance - 240) * 0.012))
        a.x += dx / distance * pull; a.y += dy / distance * pull; b.x -= dx / distance * pull; b.y -= dy / distance * pull
      }
    }
    // 最后碰撞检测：极端拓扑若未收敛，用同源节点网格降级，所有真实边仍保留。
    if (group.some((a, i) => group.some((b, j) => j > i && Math.abs(a.x-b.x)<GRAPH_NODE_WIDTH+16 && Math.abs(a.y-b.y)<GRAPH_NODE_HEIGHT+16))) group.forEach((n, i) => { n.x=(i%cols)*230; n.y=Math.floor(i/cols)*120 })
    if (graphBounds(group).width > width && width < 480) {
      // 窄屏采用可读排列，不改变关系方向，也不将排列顺序解释为父子层级。
      group.forEach((n,i)=>{n.x=GRAPH_NODE_WIDTH/2;n.y=i*112+GRAPH_NODE_HEIGHT/2})
    }
    groups.push(group)
  }
  // Pack disconnected components against the viewport aspect, not a fixed-width vertical shelf.
  // Candidate widths are bounded/deterministic; edges and component membership remain unchanged.
  const items=groups.map((group,index)=>({group,index,bounds:graphBounds(group)})).sort((a,b)=>b.bounds.height-a.bounds.height||a.index-b.index)
  if(!items.length)return[]
  const area=items.reduce((total,item)=>total+(item.bounds.width+52)*(item.bounds.height+52),0)
  const widest=Math.max(...items.map(item=>item.bounds.width))
  const aspect=Math.max(0.6,Math.min(3,width/Math.max(1,height)))
  const target=Math.sqrt(area*aspect)
  let best:PositionedGraphNode[]=[],bestScale=-1
  for(const factor of [0.75,1,1.25,1.5]){
    const targetWidth=Math.max(widest,target*factor),rows:{width:number;height:number;top:number}[]=[],positions=new Map<string,PositionedGraphNode>()
    for(const {group,bounds} of items){
      let row=rows.find(r=>r.width+bounds.width<=targetWidth)
      if(!row){const previous=rows.at(-1);row={width:0,height:bounds.height,top:previous?previous.top+previous.height+52:0};rows.push(row)}
      for(const n of group)positions.set(n.id,{...n,x:n.x-bounds.x+row.width,y:n.y-bounds.y+row.top})
      row.width+=bounds.width+52
    }
    const result=nodes.map(n=>positions.get(n.id)!),bounds=graphBounds(result),scale=Math.min(width/Math.max(1,bounds.width),height/Math.max(1,bounds.height))
    if(scale>bestScale){best=result;bestScale=scale}
  }
  return best
}
export function layoutKnowledgeGraph(nodes: CanvasGraphNode[], edges: CanvasGraphEdge[], mode: GraphLayoutMode, width = 900, height = 520): PositionedGraphNode[] {
  const graph = inspectGraph(nodes, edges)
  if (mode === 'relations') return relationLayout(graph.nodes, graph.edges, width, height)
  if (mode === 'dependency') {
    const g = new dagre.graphlib.Graph({ multigraph: true }).setGraph({ rankdir: 'LR', nodesep: 36, ranksep: 64, marginx: 0, marginy: 0 }).setDefaultEdgeLabel(() => ({}))
    for (const n of graph.nodes) g.setNode(n.id, {width: GRAPH_NODE_WIDTH, height: GRAPH_NODE_HEIGHT})
    graph.edges.forEach((e,i) => { if (e.source!==e.target) g.setEdge(e.source,e.target,{},String(i)) })
    dagre.layout(g)
    return graph.nodes.map(n=>({...n,...g.node(n.id),width:GRAPH_NODE_WIDTH,height:GRAPH_NODE_HEIGHT}))
  }
  // 分类的真实有向层级按层折行，避免宽分类拥有数百文档时生成几万像素横排。
  const incoming = new Map(graph.nodes.map(n=>[n.id,0])), children = new Map(graph.nodes.map(n=>[n.id,[] as string[]]))
  for (const e of graph.edges) if (e.source!==e.target) { incoming.set(e.target,incoming.get(e.target)!+1);children.get(e.source)!.push(e.target) }
  const ranks = new Map<string,number>(), queue = graph.nodes.filter(n=>!incoming.get(n.id)).map(n=>({id:n.id,rank:0}))
  for (let i=0;i<queue.length;i++) {const n=queue[i]!;if(ranks.has(n.id))continue;ranks.set(n.id,n.rank);for(const id of children.get(n.id)||[])queue.push({id,rank:n.rank+1})}
  for (const n of graph.nodes) if (!ranks.has(n.id)) ranks.set(n.id,0)
  const columns=Math.max(1,Math.floor(width/224)), levels=new Map<number,CanvasGraphNode[]>()
  for(const n of graph.nodes){const rank=ranks.get(n.id)!;if(!levels.has(rank))levels.set(rank,[]);levels.get(rank)!.push(n)}
  const result:PositionedGraphNode[]=[];let y=0
  for(const [,level] of [...levels].sort(([a],[b])=>a-b)) {level.forEach((n,i)=>result.push({...n,x:(i%columns)*224+92,y:y+Math.floor(i/columns)*112+38,width:184,height:76}));y+=Math.ceil(level.length/columns)*112+40}
  return result
}
/** Two lines measured in approximate 14px glyph units; short Latin names need not break at ten characters. */
export function graphLabelLines(label: string, columns = 10) {
  const remaining=Array.from(label.replace(/\s+/g,' ').trim()),budget=columns*14,lines:string[]=[]
  const glyphWidth=(char:string)=>/[MW@]/.test(char)?13.5:/[A-Z]/.test(char)?10.5:/[ilI.,:;!|' ]/.test(char)?5:/[\x00-\xff]/.test(char)?8.5:14
  for(let row=0;row<2&&remaining.length;row++){
    let count=0,width=0,lastSpace=-1
    while(count<remaining.length&&width+glyphWidth(remaining[count]!)<=budget){if(remaining[count]===' ')lastSpace=count;width+=glyphWidth(remaining[count]!);count++}
    if(row===0&&count<remaining.length&&lastSpace>0)count=lastSpace
    const chars=remaining.splice(0,Math.max(1,count))
    while(remaining[0]===' ')remaining.shift()
    if(row===1&&remaining.length){while(chars.length&&chars.reduce((n,c)=>n+glyphWidth(c),0)+14>budget)chars.pop();lines.push(chars.join('').trimEnd()+'…')}
    else lines.push(chars.join('').trim())
  }
  return lines.length?lines:['']
}
