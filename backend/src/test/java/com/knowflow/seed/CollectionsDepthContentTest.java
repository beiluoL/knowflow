package com.knowflow.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionsDepthContentTest {
    @TempDir Path directory;
    @Test void structuredReadingExamplesCompileAndDemonstrateTypesAndImmutableValues() throws Exception {
        String body=new ClassPathResource("seed/generics-reading-v5.md").getContentAsString(StandardCharsets.UTF_8);
        var match=java.util.regex.Pattern.compile("```java\\n(.*?)\\n```",java.util.regex.Pattern.DOTALL).matcher(body);List<String> blocks=new ArrayList<>();while(match.find())blocks.add(match.group(1));
        String method=blocks.stream().filter(s->s.startsWith("static <T>")).findFirst().orElseThrow();
        String types=blocks.stream().filter(s->s.startsWith("List<String> names")).findFirst().orElseThrow();
        String copy=blocks.stream().filter(s->s.startsWith("var from")).findFirst().orElseThrow();
        String immutable=blocks.stream().filter(s->s.startsWith("ConcurrentHashMap<String, List<String>>")).findFirst().orElseThrow();
        Path file=directory.resolve("ReadingV5Demo.java");Files.writeString(file,"import java.util.*;import java.util.concurrent.*;public class ReadingV5Demo {\n"+method+"\npublic static void main(String[] args){\n"+types+"\n"+copy+"\nString key=\"k\",value=\"v\";\n"+immutable+"\nSystem.out.println(groups.get(key));}}\n");
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17",file.toString()));
        Process run=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",directory.toString(),"ReadingV5Demo").redirectErrorStream(true).start();assertTrue(run.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,run.exitValue());
        assertEquals("true\njava.util.ArrayList\n[1, 2, 3]\n[v]",new String(run.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim().replace("\r\n","\n"));
    }
    @Test void java17ExperimentsCompileRunAndMatchExpectedOutput() throws Exception {
        Map<String,String> expected=Map.of("MutableKeyDemo","saved\nnull\n1\nsaved","ResizeBitDemo","1:1->1\n17:1->17\n33:1->1\n49:1->17\n65537:0->0","GenericsBoundaryDemo","[1, 2, 3]\ntrue\nget+put=1\nmerge=2\nnull rejected\nquiescent sum=1");
        for(var entry:expected.entrySet()){
            String source=new ClassPathResource("seed/"+entry.getKey()+".java.txt").getContentAsString(StandardCharsets.UTF_8);
            Path file=directory.resolve(entry.getKey()+".java");Files.writeString(file,source);
            assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17",file.toString()));
            Process run=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",directory.toString(),entry.getKey()).redirectErrorStream(true).start();
            assertTrue(run.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,run.exitValue());assertEquals(entry.getValue(),new String(run.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim().replace("\r\n","\n"));
        }
    }
    @Test void authoredChapterSeparatesApiContractFromVersionedImplementation() throws Exception {
        String body=new ClassPathResource("seed/collections-depth-hashmap.md").getContentAsString(StandardCharsets.UTF_8);
        for(String section:List.of("学习目标","Map 契约","图解","put 与 get","扩容","树化","可运行实验","fail-fast","ConcurrentHashMap","情境","官方来源"))assertTrue(body.contains(section),section);
        assertTrue(body.contains("jdk-17%2B35"));assertTrue(body.contains("2026-10-11"));assertFalse(body.contains("保证线程安全"));
    }
}
