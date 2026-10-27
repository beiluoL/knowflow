import { describe,it,expect } from 'vitest'
import { filterKnowledgeGraph, graphNodeContext } from '../knowledgeGraphView'
import type { KnowledgeGraphVO } from '@/api/types'
const graph:KnowledgeGraphVO={nodes:[{id:'c1',label:'一级',type:'category',value:0},{id:'c2',label:'二级',type:'category',value:0},{id:'d1',label:'真实文档',type:'doc',value:0},{id:'orphan',label:'未关联文档',type:'doc',value:0}],edges:[{source:'c1',target:'c2',relation:'parent'},{source:'c2',target:'d1',relation:'contains'}]}
describe('graph presentation uses actual relationships',()=>{
 it('filters a selected category and its descendants without unrelated nodes',()=>{expect(filterKnowledgeGraph(graph,'c2','').nodes.map(n=>n.id)).toEqual(['c2','d1'])})
 it('search retains real ancestor context and never invents edges',()=>{const result=filterKnowledgeGraph(graph,'all','真实');expect(result.nodes.map(n=>n.id)).toEqual(['c1','c2','d1']);expect(result.edges).toEqual(graph.edges);expect(filterKnowledgeGraph({...graph,edges:[]},'all','').edges).toEqual([])})
 it('counts only descendants and actual incident relations',()=>{const context=graphNodeContext(graph,'c2');expect(context.docs.map(n=>n.id)).toEqual(['d1']);expect(context.relationCount).toBe(2);expect(graphNodeContext(graph,'orphan').relationCount).toBe(0)})
 it('terminates safely on cyclic source relationships without duplicating nodes',()=>{const cyclic={...graph,edges:[...graph.edges,{source:'c2',target:'c1',relation:'parent' as const}]};expect(filterKnowledgeGraph(cyclic,'c1','').nodes).toHaveLength(3)})
})
