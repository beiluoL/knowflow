// @vitest-environment happy-dom
import { describe,it,expect } from 'vitest'
import { useAsyncResource } from '../useAsyncResource'
describe('async lifecycle',()=>{
  it('distinguishes rejection, retry pending, success and empty',async()=>{
    let resolve!:(value:number[])=>void;let fail=true;
    const resource=useAsyncResource(()=>fail?Promise.reject(new Error('Network Error')):new Promise<number[]>(r=>{resolve=r}),v=>v.length===0,'内容加载失败');
    await resource.run();expect(resource.status.value).toBe('error');expect(resource.error.value?.title).toBeTruthy();
    fail=false;const retry=resource.run(true);expect(resource.status.value).toBe('retrying');resolve([1]);await retry;expect(resource.status.value).toBe('ready');expect(resource.data.value).toEqual([1]);
    const empty=resource.run();resolve([]);await empty;expect(resource.status.value).toBe('empty');
  })
  it('ignores a stale completion from an older request',async()=>{
    const completions:Array<(v:number[])=>void>=[];const resource=useAsyncResource(()=>new Promise<number[]>(r=>completions.push(r)),v=>!v.length,'内容加载失败');
    const old=resource.run();const latest=resource.run();completions[1]([2]);await latest;completions[0]([1]);await old;expect(resource.data.value).toEqual([2]);
  })
})
