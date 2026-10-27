package com.knowflow.learning.actions;
import com.knowflow.ai.ModelAdapter;
import com.knowflow.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class LearningPlanAiEnhancerTest {
 AiService ai;LearningPlanAiEnhancer enhancer;PlanModels.Request req;List<PlanModels.Item> rules;
 @BeforeEach void setup(){ai=mock(AiService.class);enhancer=new LearningPlanAiEnhancer(ai,new ObjectMapper());req=new PlanModels.Request();req.setUseAi(true);rules=new DailyPlanRule().generate(req,new PlanModels.Context(List.of(),List.of(),0,Map.of()));}
 @AfterEach void close(){enhancer.close();}
 @Test void disabledAiNeverMakesProviderRequest(){req.setUseAi(false);assertEquals("RULE",enhancer.enhance(1L,req,rules).source());verifyNoInteractions(ai);}
 @Test void validCandidateOrderUsesExistingAdapterAndPreservesResources(){String keys=new ObjectMapper().valueToTree(rules.stream().map(PlanModels.Item::key).toList()).toString();when(ai.chatMulti(anyList(),anyList(),eq(1L),isNull())).thenReturn(new ModelAdapter.ChatResult("{\"orderedKeys\":"+keys+"}",List.of()));var result=enhancer.enhance(1L,req,rules);assertEquals("AI_ASSISTED",result.source());assertEquals(rules,result.tasks());}
 @Test void modelCannotCallWriteToolOrInventOwner(){var call=new ModelAdapter.ToolCall();call.name="createTasksFromPlan";call.arguments="{\"userId\":2,\"confirmed\":true}";when(ai.chatMulti(anyList(),anyList(),eq(1L),isNull())).thenReturn(new ModelAdapter.ChatResult("",List.of(call)));assertEquals("RULE",enhancer.enhance(1L,req,rules).source());assertThrows(com.knowflow.exception.BusinessException.class,()->LearningActionTools.requireReadOnlyModelCall(call.name));}
 @Test void hallucinatedResourceKeyFallsBackToExactRules(){when(ai.chatMulti(anyList(),anyList(),eq(1L),isNull())).thenReturn(new ModelAdapter.ChatResult("{\"orderedKeys\":[\"foreign-user-resource\"]}",List.of()));var r=enhancer.enhance(1L,req,rules);assertEquals("RULE",r.source());assertEquals(rules,r.tasks());}
 @Test void providerTimeoutFallsBackWithinBoundedTime(){when(ai.chatMulti(anyList(),anyList(),eq(1L),isNull())).thenAnswer(i->{Thread.sleep(12000);return new ModelAdapter.ChatResult("{}",List.of());});long start=System.nanoTime();assertEquals("RULE",enhancer.enhance(1L,req,rules).source());assertTrue((System.nanoTime()-start)/1_000_000<7000);}
}
