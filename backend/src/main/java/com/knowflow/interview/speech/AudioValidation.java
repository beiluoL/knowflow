package com.knowflow.interview.speech;
import com.knowflow.exception.BusinessException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
/** 只接受可本地确定时长的 PCM WAV；客户端报送的时长不作为真实时长证据。 */
final class AudioValidation {
 static long durationMs(byte[] bytes,String mime,int maxSeconds){
  if(!java.util.Set.of("audio/wav","audio/wave","audio/x-wav").contains(mime==null?"":mime.split(";")[0].trim().toLowerCase()))throw bad();
  if(bytes.length<44||!tag(bytes,0,"RIFF")||!tag(bytes,8,"WAVE"))throw bad();
  var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  if(Integer.toUnsignedLong(b.getInt(4))+8!=bytes.length)throw bad();
  int rate=0,alignment=0;long data=-1;boolean fmt=false;
  for(int p=12;p+8<=bytes.length;){long size=Integer.toUnsignedLong(b.getInt(p+4));long end=(long)p+8+size;if(end>bytes.length)throw bad();
   if(tag(bytes,p,"fmt ")){if(fmt||size<16)throw bad();fmt=true;int encoding=Short.toUnsignedInt(b.getShort(p+8)),channels=Short.toUnsignedInt(b.getShort(p+10)),sampleRate=b.getInt(p+12),bits=Short.toUnsignedInt(b.getShort(p+22));rate=b.getInt(p+16);alignment=Short.toUnsignedInt(b.getShort(p+20));if(encoding!=1||channels<1||channels>2||sampleRate<8000||sampleRate>96000||!java.util.Set.of(8,16,24,32).contains(bits)||alignment!=channels*bits/8||rate!=sampleRate*alignment)throw bad();}
   if(tag(bytes,p,"data")){if(data>=0)throw bad();data=size;}p=(int)(end+(size%2));
  }
  if(!fmt||data<=0||data%alignment!=0)throw bad();long ms=(data*1000+rate-1)/rate;if(ms>(long)maxSeconds*1000)throw new BusinessException(413,"录音实际时长超过限制");return ms;
 }
 private static boolean tag(byte[] a,int p,String s){return p+4<=a.length&&new String(a,p,4,StandardCharsets.US_ASCII).equals(s);}
 private static BusinessException bad(){return new BusinessException(400,"仅支持完整 PCM WAV 音频，请转换格式后重试或使用文字输入");}
}
