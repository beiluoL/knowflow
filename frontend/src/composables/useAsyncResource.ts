import { shallowRef } from 'vue'
import { describePageError } from '@/utils/error-message'
export type AsyncStatus='loading'|'ready'|'empty'|'error'|'retrying'
export function useAsyncResource<T>(loader:()=>Promise<T>,isEmpty:(data:T)=>boolean,subject:string,scope:'page'|'section'='page'){
  const status=shallowRef<AsyncStatus>('loading'),data=shallowRef<T|null>(null),error=shallowRef<ReturnType<typeof describePageError>|null>(null)
  let generation=0
  async function run(retry=false){const current=++generation;status.value=retry?'retrying':'loading';error.value=null;try{const result=await loader();if(current!==generation)return;data.value=result;status.value=isEmpty(result)?'empty':'ready'}catch(cause:unknown){if(current!==generation)return;error.value=describePageError(cause,{subject,scope});status.value='error'}}
  return {status,data,error,run}
}
