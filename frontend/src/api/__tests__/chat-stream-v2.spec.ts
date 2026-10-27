// @vitest-environment happy-dom
import {afterEach,describe,it,expect,vi} from 'vitest'
import {streamChat} from '../chat'
vi.mock('../request',()=>({apiGet:vi.fn(),apiPost:vi.fn(),apiDelete:vi.fn()}))
afterEach(()=>vi.unstubAllGlobals())
function response(text:string){vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(new ReadableStream({start(c){c.enqueue(new TextEncoder().encode(text));c.close()}}))))}
const callbacks=()=>({onToken:vi.fn(),onDone:vi.fn(),onError:vi.fn()})
describe('chat transport completion truth',()=>{
 it('does not treat an unfinished EOF as server completion',async()=>{response('event: delta\ndata: {"content":"partial"}\n\n');const cb=callbacks();await streamChat({content:'question'},cb);expect(cb.onDone).not.toHaveBeenCalled();expect(cb.onError).toHaveBeenCalledOnce()})
 it('does not treat aborted output as completed',async()=>{const control=new AbortController();vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(new ReadableStream({start(c){c.enqueue(new TextEncoder().encode('event: delta\ndata: {"content":"partial"}\n\n'))},pull(c){control.abort();c.error(new DOMException('cancelled','AbortError'))}}))));const cb=callbacks();await streamChat({content:'question'},cb,control.signal);expect(cb.onDone).not.toHaveBeenCalled();expect(cb.onError).not.toHaveBeenCalled()})
 it('accepts the authoritative done event',async()=>{response('event: delta\ndata: {"content":"part"}\n\nevent: done\ndata: {"content":"complete"}\n\n');const cb=callbacks();await streamChat({content:'question'},cb);expect(cb.onDone).toHaveBeenCalledWith('complete');expect(cb.onError).not.toHaveBeenCalled()})
})
