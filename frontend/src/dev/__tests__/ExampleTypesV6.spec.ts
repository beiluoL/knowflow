import {describe,it,expect} from 'vitest'
import {entries} from '../registry'
import {stories,storyDefaults} from '../stories'
import {createExample} from '../code-examples'
describe('all copied production SFCs',()=>{
 it('typechecks real imports required props events and v-model with vue-tsc',async()=>{
 // 仅用于 Node 的测试工具，通过动态模块名避免 Node 全局类型进入 DOM 应用环境。
 const nodePrefix='node:'
 const {mkdtempSync,writeFileSync,rmSync}=await import(nodePrefix+'fs')
 const {resolve,join}=await import(nodePrefix+'path')
 const {spawnSync}=await import(nodePrefix+'child_process')
 const {default:process}=await import(nodePrefix+'process')
 const dir=mkdtempSync(resolve('.example-types-'))
 try{
 let count=0
 for(const story of stories){const entry=entries.find(e=>e.id===story.componentId)!;if(entry.implementationStatus!=='已接入真实组件')continue;writeFileSync(join(dir,`${entry.id}-${story.storyId}.vue`),createExample(entry,storyDefaults(entry,story)));count++}
 writeFileSync(join(dir,'tsconfig.json'),JSON.stringify({extends:resolve('tsconfig.app.json'),compilerOptions:{noUnusedLocals:false,noUnusedParameters:false,types:['vite/client'],paths:{'@/*':[resolve('src/*')],vue:[resolve('node_modules/vue')]}},include:['*.vue',resolve('src/**/*.d.ts')]}))
 const result=spawnSync(process.execPath,[resolve('node_modules/vue-tsc/bin/vue-tsc.js'),'-p',join(dir,'tsconfig.json'),'--noEmit'],{encoding:'utf8',timeout:90000})
 expect(count).toBeGreaterThan(30);expect(result.stdout+result.stderr).toBe('');expect(result.status).toBe(0)
 }finally{rmSync(dir,{recursive:true,force:true})}
 },100000)
})
