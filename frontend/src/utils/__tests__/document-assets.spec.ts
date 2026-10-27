import { describe, it, expect, vi } from 'vitest'
const {get}=vi.hoisted(()=>({get:vi.fn()}))
vi.mock('@/api/request',()=>({default:{get}}))
import { fetchDocumentAsset } from '@/utils/document-assets'
describe('document asset authentication boundary',()=>{
  it('only requests local upload assets using the authenticated client',async()=>{
    const blob=new Blob(['fixture']);get.mockResolvedValueOnce(blob)
    expect(await fetchDocumentAsset('/uploads/safe.md')).toBe(blob)
    expect(get).toHaveBeenCalledWith('/uploads/safe.md',{baseURL:'',responseType:'blob'})
  })
  it('never sends credentials to a remote or ambiguous URL',async()=>{
    get.mockClear()
    for(const url of ['https://evil.example/x','//evil.example/x','/uploads/../api/admin','/uploads/%2e%2e/x','/uploads\\evil','/uploads/x?token=x']){
      await expect(fetchDocumentAsset(url)).rejects.toThrow()
    }
    expect(get).not.toHaveBeenCalled()
  })
  it('propagates access denial rather than making an empty download',async()=>{
    get.mockRejectedValueOnce(new Error('forbidden'))
    await expect(fetchDocumentAsset('/uploads/safe.md')).rejects.toThrow('forbidden')
  })
})
