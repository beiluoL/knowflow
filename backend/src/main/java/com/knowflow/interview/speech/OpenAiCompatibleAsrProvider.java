package com.knowflow.interview.speech;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
/** 服务端代理；不重试付费请求，错误不带远端正文或密钥。 */
@Component
public class OpenAiCompatibleAsrProvider implements InterviewAsrProvider {
 private final SpeechProperties config;private final ObjectMapper json;
 public OpenAiCompatibleAsrProvider(SpeechProperties config,ObjectMapper json){this.config=config;this.json=json;}
 public boolean capability(){try{var u=URI.create(config.getEndpoint());return config.isEnabled()&&!config.getApiKey().isBlank()&&!config.getModel().isBlank()&&Set.of("http","https").contains(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
 public boolean healthCheck(){return capability();}
 public List<String> supportedFormats(){return List.of("audio/wav");}
 public String auditName(){return "OPENAI_COMPATIBLE_ASR";}
 public String transcribe(byte[] audio){
  if(!capability())throw new IllegalStateException("ASR disabled");
  try{String boundary="kf-"+UUID.randomUUID();var out=new ByteArrayOutputStream();
   out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n"+config.getModel()+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(audio);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
   var request=HttpRequest.newBuilder(URI.create(config.getEndpoint())).timeout(Duration.ofSeconds(Math.max(1,Math.min(120,config.getTimeoutSeconds())))).header("Authorization","Bearer "+config.getApiKey()).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
   var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
   // 同时限制响应头与正文；InputStream的阻塞读取不受request.timeout完整保护。
   var pending=client.sendAsync(request,info->boundedBody());
   HttpResponse<byte[]> response;
   try{response=pending.get(Math.max(1,Math.min(120,config.getTimeoutSeconds())),TimeUnit.SECONDS);}
   finally{if(!pending.isDone())pending.cancel(true);}
   byte[] bytes=response.body();if(response.statusCode()!=200)throw new IOException("ASR response rejected");var root=json.readTree(bytes);var text=root.get("text");if(text==null||!text.isTextual()||text.asText().isBlank()||text.asText().length()>12000)throw new IOException("ASR transcript rejected");return text.asText();
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("ASR unavailable");}catch(Exception e){throw new IllegalStateException("ASR unavailable");}
 }
 /** 在分配完整响应前拒绝超限数据，不把有界读取改成无界byte[]缓存。 */
 private static HttpResponse.BodySubscriber<byte[]> boundedBody(){
  var delegate=HttpResponse.BodySubscribers.ofByteArray();
  return new HttpResponse.BodySubscriber<>(){
   private Flow.Subscription subscription;
   private int received;
   private boolean rejected;
   public CompletionStage<byte[]> getBody(){return delegate.getBody();}
   public void onSubscribe(Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}
   public void onNext(List<ByteBuffer> buffers){
    if(rejected)return;
    for(var buffer:buffers){
     if(buffer.remaining()>65536-received){rejected=true;subscription.cancel();delegate.onError(new IOException("ASR response rejected"));return;}
     received+=buffer.remaining();
    }
    delegate.onNext(buffers);
   }
   public void onError(Throwable error){if(!rejected)delegate.onError(error);}
   public void onComplete(){if(!rejected)delegate.onComplete();}
  };
 }

}
