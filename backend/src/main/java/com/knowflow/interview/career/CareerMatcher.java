package com.knowflow.interview.career;

import java.util.*;

/** 只比较已确认的结构化技能与显式别名，不将文本包含或招聘概率当匹配。 */
public final class CareerMatcher {
    private CareerMatcher() { }
    /**
     * 一条用户确认的结构化资料。
     *
     * <p>value/quote 是匹配与证据不可绕过的核心字段；fields 仅用于简历编辑器的结构化展示
     * （如公司、职位、时间、技术栈），不参与匹配评分，也不能替代 quote 原文证据。</p>
     */
    public record Claim(String value, String quote, Map<String, String> fields) {
        public Claim(String value, String quote) {
            this(value, quote, Map.of());
        }

        public Claim {
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }
    }
    public record Dimension(String requirement, List<String> resumeEvidence, String matchStatus,
                            String gapDescription, String evidenceSource) { }
    private static final Map<String,String> ALIASES = Map.ofEntries(
            Map.entry("java多线程","java-concurrency"), Map.entry("java并发","java-concurrency"),
            Map.entry("多线程","java-concurrency"), Map.entry("javaconcurrency","java-concurrency"), Map.entry("jvmgc","jvm"),
            Map.entry("springboot","spring-boot"), Map.entry("toolcalling","agent"),
            Map.entry("检索增强生成","rag"), Map.entry("模型上下文协议","mcp"));
    public static String canonical(String value) {
        String key=value.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]", "");
        return ALIASES.getOrDefault(key,key);
    }
    public static List<Dimension> match(List<Claim> resume, List<Claim> required) {
        List<Dimension> result=new ArrayList<>();
        for (Claim requirement:required) {
            var hits=resume.stream().filter(c->canonical(c.value()).equals(canonical(requirement.value())))
                    .map(Claim::quote).distinct().toList();
            result.add(new Dimension(requirement.value(),hits,hits.isEmpty()?"MISSING":"MATCHED",
                    hits.isEmpty()?"已确认简历中未提供该技能证据，不代表本人不会":"仅证明简历中有该项自述，未验证熟练度",
                    "USER_CONFIRMED_RESUME_AND_JD"));
        }
        return List.copyOf(result);
    }
    private static final int MAX_FIELD_COUNT = 8;
    private static final int MAX_FIELD_VALUE_LENGTH = 2000;

    public static void validate(List<Claim> claims,String source) {
        if(claims==null||claims.size()>80)throw new IllegalArgumentException("结构化条目数量无效");
        for(Claim claim:claims) {
            if(claim==null||claim.value()==null||claim.value().isBlank()||claim.value().length()>500
                    ||claim.quote()==null||claim.quote().isBlank()||claim.quote().length()>2000||!source.contains(claim.quote()))
                throw new IllegalArgumentException("每项信息必须引用原文连续片段");
            if(claim.fields()!=null&&!claim.fields().isEmpty()){
                if(claim.fields().size()>MAX_FIELD_COUNT)throw new IllegalArgumentException("结构化字段数量无效");
                for(var entry:claim.fields().entrySet()){
                    if(entry.getKey()==null||entry.getKey().isBlank()||entry.getKey().length()>48
                            ||entry.getValue()==null||entry.getValue().length()>MAX_FIELD_VALUE_LENGTH)
                        throw new IllegalArgumentException("结构化字段名称或内容无效");
                }
            }
        }
    }
}
