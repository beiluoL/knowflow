import { describe, it, expect } from 'vitest'
import { buildLearningCoachContext } from '../context'
import { renderCoachContext } from '../prompt'
const diagnosis={type:'LOW_RECALL' as const,title:'记忆能力偏弱',reason:'来自真实聚合',dimension:'RECALL' as const,score:45,sampleCount:2,confidence:.25,severity:'MEDIUM' as const,priority:90}
const base={reviewRequired:[],weakPoints:[],tasks:[]}
describe('coach consumes the unique learning snapshot without diagnosis recomputation',()=>{
 it('keeps empty evidence unknown rather than healthy',()=>{const ctx=buildLearningCoachContext({...base,learningState:{activeGoal:null,context:{},evidenceAggregate:null,behaviorDiagnoses:[]}});expect(ctx.hasData).toBe(false);expect(ctx.learningState?.diagnoses).toEqual([]);expect(renderCoachContext(ctx)).toContain('暂无足够数据做诊断');expect(renderCoachContext(ctx)).not.toContain('学习状态良好')})
 it('serializes actual goal, knowledge, samples and confidence without privileged store fields',()=>{const snapshot={activeGoal:{title:'学习并发'},context:{knowledgeTitle:'CAS'},evidenceAggregate:{totalEvidence:2,activeDays:1},behaviorDiagnoses:[diagnosis],coin:999,apiKey:'fixture-secret-not-real',userId:123};const ctx=buildLearningCoachContext({...base,learningState:snapshot});const text=renderCoachContext(ctx);expect(text).toContain('当前目标：学习并发');expect(text).toContain('当前知识：CAS');expect(text).toContain('45 分，2 条证据，置信度 25%');const data=JSON.stringify(ctx);expect(data).not.toContain('apiKey');expect(data).not.toContain('fixture-secret');expect(data).not.toContain('coin');expect(data).not.toContain('userId')})
 it('does not project diagnoses without evidence even if a stale snapshot supplies them',()=>{const ctx=buildLearningCoachContext({...base,learningState:{evidenceAggregate:{totalEvidence:0,activeDays:0},behaviorDiagnoses:[diagnosis]}});expect(ctx.learningState?.diagnoses).toEqual([]);expect(renderCoachContext(ctx)).not.toContain('记忆能力偏弱')})
})
