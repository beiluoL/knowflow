import { describe, expect, it } from 'vitest'
import { resolveAppNavChildren } from '@/apps/navigation'
describe('考试侧栏入口',()=>{
 it('安装启用后出现在学习分组',()=>{expect(resolveAppNavChildren({exam:{installed:true,enabled:true,sortOrder:8}}).learning).toContainEqual(expect.objectContaining({label:'考试',path:'/exam'}))})
 it('未安装或停用时不显示',()=>{for(const state of [{installed:false,enabled:false,sortOrder:8},{installed:true,enabled:false,sortOrder:8}])expect(resolveAppNavChildren({exam:state}).learning.some(c=>c.path==='/exam')).toBe(false)})
})
