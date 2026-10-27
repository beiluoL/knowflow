package com.knowflow.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

/** Authored learning content, not an event fixture. Fail closed on broken references. */
@Component
public class InterviewSeedCatalog {
    public static final String KEY="java-ai-interview-v1";
    private static final List<String> ORDER=List.of("java-core","concurrency","jvm","spring-data","llm","rag","agent","project");
    private final ObjectMapper json;
    private volatile List<JsonNode> content;
    public InterviewSeedCatalog(ObjectMapper json) {this.json=json;}
    public synchronized List<JsonNode> paths() {
        if(content!=null) return content;
        Map<String,JsonNode> paths=new HashMap<>(); Set<String> keys=new HashSet<>();
        try {
            for(String file:List.of("java","ai","project")) {
                try(var in=new ClassPathResource("seed/java-ai-v1-"+file+".json").getInputStream()) {
                    for(JsonNode path:json.readTree(in).required("paths")) {
                        String key=text(path,"key"); require(paths.put(key,path)==null,"duplicate path");
                        text(path,"title");text(path,"description");text(path,"tag");
                        require(path.required("chapters").size()==4,"four chapters required");
                        require(path.required("notes").size()==3,"three notes required");
                        Set<String> previous=new HashSet<>();
                        for(JsonNode ch:path.required("chapters")) {
                            String ck=text(ch,"key"); require(keys.add(ck),"duplicate chapter");
                            for(JsonNode dependency:ch.required("prerequisites")) require(previous.contains(dependency.asText()),"prerequisite must precede chapter in same path");
                            previous.add(ck); text(ch,"title");text(ch,"description");
                            require(ch.required("objectives").size()>0,"objectives required");
                            require(ch.required("minutes").asInt()>0,"minutes required");
                            require(ch.required("difficulty").asInt()>=1&&ch.required("difficulty").asInt()<=3,"difficulty");
                            require(Set.of("S","A","B").contains(text(ch,"importance")),"importance");
                            require(text(ch,"material").length()>=350,"substantive material required");
                            require(ch.required("knowledge").size()==3&&ch.required("flashcards").size()==4&&ch.required("questions").size()==3,"chapter counts");
                            Set<String> knowledge=new HashSet<>();
                            for(JsonNode k:ch.required("knowledge")) {String kk=text(k,"key");require(keys.add(kk),"duplicate knowledge"); knowledge.add(kk);text(k,"name");text(k,"explanation");}
                            for(JsonNode f:ch.required("flashcards")) {text(f,"front");text(f,"back");text(f,"type");references(f,knowledge);}
                            for(JsonNode q:ch.required("questions")) {text(q,"question");text(q,"answer");require(q.required("keyPoints").size()>0&&q.required("followUps").size()>0&&q.required("commonMistakes").size()>0,"question structure");require(Set.of("S","A","B").contains(text(q,"rank")),"question rank");references(q,knowledge);}
                            var exercise=ch.required("exercise");text(exercise,"title");text(exercise,"description");text(exercise,"codeTemplate");exercise.required("testCases");references(exercise,knowledge);
                        }
                        for(JsonNode note:path.required("notes")) {text(note,"title");text(note,"content"); require(previous.contains(text(note,"chapterKey")),"note chapter");}
                    }
                }
            }
        } catch(IOException e) {throw new IllegalStateException("Interview seed content unavailable",e);}
        require(paths.keySet().equals(new HashSet<>(ORDER)),"eight canonical paths required");
        content=ORDER.stream().map(paths::get).toList(); return content;
    }
    private void references(JsonNode node,Set<String> keys) {
        require(node.required("knowledgeKeys").size()>0,"knowledge reference required");
        for(JsonNode ref:node.required("knowledgeKeys")) require(keys.contains(ref.asText()),"unknown chapter knowledge reference");
    }
    public static String text(JsonNode node,String key) {String value=node.required(key).asText();require(!value.isBlank(),"empty "+key);return value;}
    private static void require(boolean valid,String reason) {if(!valid)throw new IllegalStateException("Invalid interview seed: "+reason);}
}
