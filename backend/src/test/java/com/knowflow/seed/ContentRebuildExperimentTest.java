package com.knowflow.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContentRebuildExperimentTest {
    @TempDir Path directory;
    @Test void authoredExperimentsRunAgainstJava17AndRealSpringTransactionBoundary()throws Exception{
        Map<String,String> expected=Map.of("HashContractRebuild","null\n1\nsaved","ChmAtomicRebuild","1\n2","LockConditionRebuild","2\nfalse\n0","SpringTransactionRebuild","external=0\nself=1","RagMetricsRebuild","recall@3=1.00\nrr=0.50\nanswer correctness requires separate labels");
        String cp=System.getProperty("java.class.path");
        for(var entry:expected.entrySet()){
            Path file=directory.resolve(entry.getKey()+".java");Files.writeString(file,new ClassPathResource("seed/rebuild/"+entry.getKey()+".java.txt").getContentAsString(StandardCharsets.UTF_8));
            assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-classpath",cp,file.toString()),entry.getKey());
            Process run=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",directory+java.io.File.pathSeparator+cp,entry.getKey()).redirectErrorStream(true).start();
            assertTrue(run.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,run.exitValue());
            String output=new String(run.getInputStream().readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
            String actual=output.lines().filter(s->!s.contains("DEBUG")&&!s.contains("INFO")&&!s.contains("WARN")).reduce((a,b)->a+"\n"+b).orElse("").trim();
            assertEquals(entry.getValue(),actual,entry.getKey());
        }
    }
}
