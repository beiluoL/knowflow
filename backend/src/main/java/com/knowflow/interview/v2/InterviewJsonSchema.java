package com.knowflow.interview.v2;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** 本地执行面试JSON Schema所用的有界子集，不下载模型给出的schema或执行任何代码。 */
final class InterviewJsonSchema {
 static void validate(JsonNode value,JsonNode schema){
  JsonNode type=schema.get("type");
  if(type!=null){boolean ok=false;if(type.isArray()){for(var item:type)ok|=matches(value,item.asText());}else ok=matches(value,type.asText());if(!ok)throw new IllegalArgumentException("schema type");}
  if(schema.has("enum")){boolean ok=false;for(var item:schema.get("enum"))ok|=item.equals(value);if(!ok)throw new IllegalArgumentException("schema enum");}
  if(value.isObject()){
   if(schema.has("required"))for(var key:schema.get("required"))if(!value.has(key.asText()))throw new IllegalArgumentException("schema required");
   var properties=schema.path("properties");var fields=value.fields();while(fields.hasNext()){var entry=fields.next();if(properties.has(entry.getKey()))validate(entry.getValue(),properties.get(entry.getKey()));else if(schema.has("additionalProperties")&&!schema.get("additionalProperties").asBoolean())throw new IllegalArgumentException("schema extra field");}
  }
  if(value.isArray()){
   bound(value.size(),schema,"minItems","maxItems");for(var child:value)validate(child,schema.path("items"));
  }
  if(value.isTextual())bound(value.asText().length(),schema,"minLength","maxLength");
  if(value.isNumber())bound(value.asDouble(),schema,"minimum","maximum");
 }
 private static void bound(double n,JsonNode s,String min,String max){if(s.has(min)&&n<s.get(min).asDouble()||s.has(max)&&n>s.get(max).asDouble())throw new IllegalArgumentException("schema bound");}
 private static boolean matches(JsonNode n,String type){return switch(type){case "object"->n.isObject();case "array"->n.isArray();case "string"->n.isTextual();case "integer"->n.isIntegralNumber();case "number"->n.isNumber();case "null"->n.isNull();default->false;};}
}
