package com.knowflow.interview.speech;
import java.util.*;
/**
 * 定义面试音频转写供应方契约。
 */
public interface InterviewAsrProvider {
 String transcribe(byte[] audio);
 boolean capability();
 List<String> supportedFormats();
 /** 无付费探测，仅检查配置是否具备调用条件。 */
 boolean healthCheck();
 String auditName();
}
