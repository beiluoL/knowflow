package com.knowflow.catalog;
import com.fasterxml.jackson.databind.ObjectMapper;
/** 管理端编辑上下文指纹，不包含答案内容本身；不是题目规范身份。 */
public final class QuestionEditToken {
 private QuestionEditToken(){}
 public static String of(ObjectMapper mapper,Object value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));}catch(java.security.NoSuchAlgorithmException|com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot create catalog edit token",e);}}
}
