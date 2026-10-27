import { onBeforeUnmount, ref } from 'vue'
export function encodeWav(buffer:AudioBuffer):Blob {
 const channels=buffer.numberOfChannels, size=buffer.length*channels*2
 const data=new DataView(new ArrayBuffer(44+size)); const text=(at:number,s:string)=>{for(let i=0;i<s.length;i++)data.setUint8(at+i,s.charCodeAt(i))}
 text(0,'RIFF');data.setUint32(4,36+size,true);text(8,'WAVE');text(12,'fmt ');data.setUint32(16,16,true);data.setUint16(20,1,true);data.setUint16(22,channels,true);data.setUint32(24,buffer.sampleRate,true);data.setUint32(28,buffer.sampleRate*channels*2,true);data.setUint16(32,channels*2,true);data.setUint16(34,16,true);text(36,'data');data.setUint32(40,size,true)
 for(let i=0;i<buffer.length;i++)for(let c=0;c<channels;c++){const s=Math.max(-1,Math.min(1,buffer.getChannelData(c)[i]??0));data.setInt16(44+(i*channels+c)*2,s<0?s*32768:s*32767,true)}
 return new Blob([data],{type:'audio/wav'})
}
export function useInterviewRecorder() {
 const state=ref<'idle'|'recording'|'paused'|'processing'|'ready'>('idle'),error=ref(''),seconds=ref(0),volume=ref(0),blob=ref<Blob|null>(null)
 let recorder:MediaRecorder|undefined,stream:MediaStream|undefined,context:AudioContext|undefined,timer:ReturnType<typeof setInterval>|undefined,cancelled=false
 function release(){clearInterval(timer);stream?.getTracks().forEach(t=>t.stop());stream=undefined;void context?.close();context=undefined;volume.value=0}
 function discard(){cancelled=true;if(recorder?.state!=='inactive')recorder?.stop();release();blob.value=null;seconds.value=0;state.value='idle'}
 async function start(maxSeconds:number,maxBytes:number){discard();cancelled=false;error.value=''
 try {
 if(!navigator.mediaDevices?.getUserMedia||typeof MediaRecorder==='undefined')throw new Error('浏览器不支持录音，请使用文字作答')
 stream=await navigator.mediaDevices.getUserMedia({audio:true});if(cancelled){release();return}
 context=new AudioContext();const analyser=context.createAnalyser();context.createMediaStreamSource(stream).connect(analyser);const levels=new Uint8Array(analyser.frequencyBinCount)
 const mime=['audio/webm;codecs=opus','audio/mp4','audio/ogg;codecs=opus','audio/webm'].find(t=>MediaRecorder.isTypeSupported(t));recorder=new MediaRecorder(stream,mime?{mimeType:mime}:undefined);const chunks:Blob[]=[];let bytes=0
 recorder.ondataavailable=e=>{bytes+=e.data.size;if(bytes>maxBytes){error.value='录音超过大小限制，请缩短后重录';discard()}else chunks.push(e.data)}
 recorder.onstop=async()=>{if(cancelled)return;state.value='processing';try{const native=new Blob(chunks,{type:recorder?.mimeType});const decoded=await context!.decodeAudioData(await native.arrayBuffer());if(decoded.duration>maxSeconds)throw new Error('录音超过时长限制');const rate=16000,length=Math.floor(decoded.duration*rate);if(length*2+44>maxBytes)throw new Error('录音超过大小限制');const samples=new Float32Array(length);for(let i=0;i<length;i++){const at=Math.min(decoded.length-1,Math.floor(i*decoded.sampleRate/rate));for(let c=0;c<decoded.numberOfChannels;c++)samples[i]!+=decoded.getChannelData(c)[at]!/decoded.numberOfChannels}const result=encodeWav({numberOfChannels:1,length,sampleRate:rate,getChannelData:()=>samples} as unknown as AudioBuffer);if(!cancelled){blob.value=result;seconds.value=decoded.duration;state.value='ready'}}catch(e){error.value=e instanceof Error?e.message:'录音处理失败，请使用文字作答';state.value='idle'}finally{release()}}
 recorder.start(500);state.value='recording';timer=setInterval(()=>{if(state.value==='recording'){seconds.value+=.1;analyser.getByteFrequencyData(levels);volume.value=levels.reduce((a,b)=>a+b,0)/levels.length/255;if(seconds.value>=maxSeconds)stop()}},100)
 }catch(e){error.value=e instanceof Error&&e.name==='NotAllowedError'?'麦克风权限被拒绝，请在浏览器中允许麦克风，或使用文字作答':e instanceof Error?e.message:'麦克风不可用，请使用文字作答';release();state.value='idle'}}
 function stop(){if(recorder&&recorder.state!=='inactive'){clearInterval(timer);recorder.stop()}}
 function pause(){if(recorder?.state==='recording'){recorder.pause();state.value='paused'}}
 function resume(){if(recorder?.state==='paused'){recorder.resume();state.value='recording'}}
 onBeforeUnmount(discard)
 return {state,error,seconds,volume,blob,start,stop,pause,resume,discard}
}
