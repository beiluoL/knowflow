import type { KnowledgeGraphVO } from '@/api/types'
function descendants(graph: KnowledgeGraphVO, root: string): Set<string> {
 const found=new Set([root]), pending=[root]
 while(pending.length){const id=pending.pop()!;for(const edge of graph.edges)if(edge.source===id&&!found.has(edge.target)){found.add(edge.target);pending.push(edge.target)}}
 return found
}
/** Pure presentation filter. Every returned node and edge must already exist in the server graph. */
export function filterKnowledgeGraph(graph:KnowledgeGraphVO,categoryId:string,keyword:string):KnowledgeGraphVO {
 const allowed=categoryId==='all'?new Set(graph.nodes.map(n=>n.id)):descendants(graph,categoryId)
 const query=keyword.trim().toLocaleLowerCase()
 let visible=allowed
 if(query){visible=new Set(graph.nodes.filter(n=>allowed.has(n.id)&&n.label.toLocaleLowerCase().includes(query)).map(n=>n.id));const pending=[...visible];while(pending.length){const id=pending.pop()!;for(const edge of graph.edges)if(edge.target===id&&allowed.has(edge.source)&&!visible.has(edge.source)){visible.add(edge.source);pending.push(edge.source)}}}
 return {nodes:graph.nodes.filter(n=>visible.has(n.id)),edges:graph.edges.filter(e=>visible.has(e.source)&&visible.has(e.target))}
}
export function graphNodeContext(graph:KnowledgeGraphVO,id:string){
 const ids=descendants(graph,id)
 return {docs:graph.nodes.filter(n=>ids.has(n.id)&&n.type==='doc'),relationCount:graph.edges.filter(e=>e.source===id||e.target===id).length}
}
