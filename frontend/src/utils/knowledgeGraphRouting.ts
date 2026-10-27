import { graphBounds, type CanvasGraphEdge, type GraphLayoutMode, type PositionedGraphNode } from './knowledgeGraphLayout'

export interface GraphPoint { x: number; y: number }
export interface RoutedGraphEdge extends CanvasGraphEdge { points: GraphPoint[]; path: string; obstructed?: boolean }
export interface GraphEdgeLabel { index: number; x: number; y: number; width: number; height: number; text: string; displayText: string }
interface Rect { left: number; right: number; top: number; bottom: number }
const rectangle = (n: PositionedGraphNode, gap = 0): Rect => ({ left: n.x-n.width/2-gap, right: n.x+n.width/2+gap, top: n.y-n.height/2-gap, bottom: n.y+n.height/2+gap })
const inside = (p: GraphPoint, r: Rect) => p.x>r.left+0.001 && p.x<r.right-0.001 && p.y>r.top+0.001 && p.y<r.bottom-0.001
function crosses(a: GraphPoint, b: GraphPoint, r: Rect) {
  if (a.x === b.x) return a.x>r.left+0.001 && a.x<r.right-0.001 && Math.max(a.y,b.y)>r.top+0.001 && Math.min(a.y,b.y)<r.bottom-0.001
  if (a.y === b.y) return a.y>r.top+0.001 && a.y<r.bottom-0.001 && Math.max(a.x,b.x)>r.left+0.001 && Math.min(a.x,b.x)<r.right-0.001
  return true
}
const distance=(a:GraphPoint,b:GraphPoint)=>Math.abs(a.x-b.x)+Math.abs(a.y-b.y)
function simplify(points: GraphPoint[]) {
  const result:GraphPoint[]=[]
  for(const p of points){const b=result.at(-1),a=result.at(-2);if(b&&p.x===b.x&&p.y===b.y)continue;if(a&&b&&((a.x===b.x&&b.x===p.x)||(a.y===b.y&&b.y===p.y)))result.pop();result.push(p)}
  return result
}
/** Obstacle visibility grid in CSS-pixel graph coordinates; no new relations or random routing. */
function orthogonal(start: GraphPoint, end: GraphPoint, obstacles: Rect[]): GraphPoint[] | null {
  const clear=(a:GraphPoint,b:GraphPoint)=>!obstacles.some(r=>crosses(a,b,r))
  const direct=[[start,{x:end.x,y:start.y},end],[start,{x:start.x,y:end.y},end]]
  for(const points of direct)if(points.slice(1).every((p,i)=>clear(points[i]!,p)))return simplify(points)
  const xs=[...new Set([start.x,end.x,...obstacles.flatMap(r=>[r.left,r.right])])].sort((a,b)=>a-b)
  const ys=[...new Set([start.y,end.y,...obstacles.flatMap(r=>[r.top,r.bottom])])].sort((a,b)=>a-b)
  const width=xs.length,key=(x:number,y:number)=>y*width+x,point=(id:number):GraphPoint=>({x:xs[id%width]!,y:ys[Math.floor(id/width)]!})
  const from=key(xs.indexOf(start.x),ys.indexOf(start.y)),to=key(xs.indexOf(end.x),ys.indexOf(end.y))
  const costs=new Map<number,number>([[from,0]]),parent=new Map<number,number>(),open=new Set([from]),closed=new Set<number>(),blocked=new Map<number,boolean>()
  while(open.size){
    let id=-1,best=Infinity
    for(const candidate of open){const cost=costs.get(candidate)!+distance(point(candidate),end);if(cost<best){id=candidate;best=cost}}
    if(id===to){const result=[end];while(id!==from){id=parent.get(id)!;result.push(point(id))}return simplify(result.reverse())}
    open.delete(id);closed.add(id)
    const x=id%width,y=Math.floor(id/width),a=point(id)
    const neighbors:[number,number][]=[[x-1,y],[x+1,y],[x,y-1],[x,y+1]]
    for(const [nx,ny] of neighbors){
      if(nx<0||nx>=width||ny<0||ny>=ys.length)continue
      const next=key(nx,ny);if(closed.has(next))continue
      const b=point(next)
      if(!blocked.has(next))blocked.set(next,obstacles.some(r=>inside(b,r)))
      if(blocked.get(next)||!clear(a,b))continue
      const cost=costs.get(id)!+distance(a,b)
      if(cost>=(costs.get(next)??Infinity))continue
      costs.set(next,cost);parent.set(next,id);open.add(next)
    }
  }
  return null
}
function port(node:PositionedGraphNode,side:'left'|'right'|'top'|'bottom',gap:number):[GraphPoint,GraphPoint]{
  const x=side==='left'?node.x-node.width/2:side==='right'?node.x+node.width/2:node.x
  const y=side==='top'?node.y-node.height/2:side==='bottom'?node.y+node.height/2:node.y
  return [{x,y},{x:x+(side==='right'?gap:side==='left'?-gap:0),y:y+(side==='bottom'?gap:side==='top'?-gap:0)}]
}
export function routeGraphEdges(nodes:PositionedGraphNode[],edges:CanvasGraphEdge[],mode:GraphLayoutMode):RoutedGraphEdge[]{
  const index=new Map(nodes.map(n=>[n.id,n]))
  return edges.flatMap(edge=>{
    const a=index.get(edge.source),b=index.get(edge.target);if(!a||!b)return[]
    const horizontal=mode==='dependency'||Math.abs(b.x-a.x)>=Math.abs(b.y-a.y)
    const source=mode==='dependency'?'right':horizontal?(b.x>=a.x?'right':'left'):(b.y>=a.y?'bottom':'top')
    const target=a===b?'top':mode==='dependency'?'left':horizontal?(b.x>=a.x?'left':'right'):(b.y>=a.y?'top':'bottom')
    for(const gap of [12,4]){
      const [start,out]=port(a,source,gap),[end,into]=port(b,target,gap),middle=orthogonal(out,into,nodes.map(n=>rectangle(n,gap)))
      if(middle){const points=simplify([start,...middle,end]);return[{...edge,points,path:points.map((p,i)=>`${i?'L':'M'} ${p.x} ${p.y}`).join(' ')}]}
    }
    // Invalid/overlapping external coordinates remain visible and explicitly warned, never silently drop an edge.
    const [start]=port(a,source,0),[end]=port(b,target,0),points=[start,end]
    return[{...edge,points,path:`M ${start.x} ${start.y} L ${end.x} ${end.y}`,obstructed:true}]
  })
}
export function routedGraphBounds(nodes:PositionedGraphNode[],edges:RoutedGraphEdge[],labels:GraphEdgeLabel[]=[]){
  const bounds=graphBounds(nodes);if(!nodes.length)return bounds
  const points=[...edges.flatMap(e=>e.points),...labels.flatMap(label=>[{x:label.x-label.width/2,y:label.y-label.height/2},{x:label.x+label.width/2,y:label.y+label.height/2}])],left=Math.min(bounds.x,...points.map(p=>p.x)),top=Math.min(bounds.y,...points.map(p=>p.y))
  return{x:left,y:top,width:Math.max(bounds.x+bounds.width,...points.map(p=>p.x))-left,height:Math.max(bounds.y+bounds.height,...points.map(p=>p.y))-top}
}
/** Only selected relation labels with safe free space are painted. Full labels remain in the relation list/title. */
export function placeGraphEdgeLabels(edges:RoutedGraphEdge[],nodes:PositionedGraphNode[],selectedId?:string|null):GraphEdgeLabel[]{
  if(!selectedId)return[]
  const used:Rect[]=nodes.map(n=>rectangle(n,8)),result:GraphEdgeLabel[]=[]
  edges.forEach((edge,index)=>{
    if(edge.source!==selectedId&&edge.target!==selectedId)return
    const text=edge.label||edge.relation,characters=Array.from(text),displayText=characters.length>12?characters.slice(0,12).join('')+'…':text
    const glyphWidth=(c:string)=>c.codePointAt(0)!>255?11:/[MW@]/.test(c)?10.5:/[A-Z]/.test(c)?8.25:/[ilI.,:;!|' ]/.test(c)?4:6.75
    const width=Math.min(168,Math.max(44,Array.from(displayText).reduce((n,c)=>n+glyphWidth(c),0)+16)),height=24
    const segments=edge.points.slice(1).map((p,i)=>({a:edge.points[i]!,b:p})).sort((a,b)=>distance(b.a,b.b)-distance(a.a,a.b))
    for(const {a,b} of segments)for(const offset of [-18,18]){
      const x=(a.x+b.x)/2+(a.x===b.x?offset+Math.sign(offset)*width/2:0),y=(a.y+b.y)/2+(a.y===b.y?offset:0),rect={left:x-width/2,right:x+width/2,top:y-height/2,bottom:y+height/2}
      if(used.some(r=>rect.left<r.right&&rect.right>r.left&&rect.top<r.bottom&&rect.bottom>r.top))continue
      used.push(rect);result.push({index,x,y,width,height,text,displayText});return
    }
  })
  return result
}
