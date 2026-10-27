import { describe, it, expect } from 'vitest'
import { encodeWav } from '../useInterviewRecorder'
describe('interview PCM WAV', () => {
 it('writes real PCM duration and RIFF metadata', async () => {
 const blob = encodeWav({numberOfChannels:1,length:4,sampleRate:8000,getChannelData:()=>new Float32Array([-1,0,1,.5])} as unknown as AudioBuffer)
 expect(blob.type).toBe('audio/wav'); expect(blob.size).toBe(52)
 const v = new DataView(await blob.arrayBuffer()); expect(v.getUint32(24,true)).toBe(8000); expect(v.getInt16(44,true)).toBe(-32768)
 })
})
