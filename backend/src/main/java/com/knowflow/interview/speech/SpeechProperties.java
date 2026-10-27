package com.knowflow.interview.speech;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
/** 语音独立开关；密钥仅由服务端环境注入，绝不返回客户端。 */
@Data @Configuration @ConfigurationProperties(prefix="knowflow.interview.speech")
public class SpeechProperties {
 private boolean enabled=false;
 private String endpoint="";
 @lombok.ToString.Exclude private String apiKey="";
 private String model="";
 private long maxBytes=10*1024*1024;
 private int maxDurationSeconds=120;
 private int timeoutSeconds=45;
}
