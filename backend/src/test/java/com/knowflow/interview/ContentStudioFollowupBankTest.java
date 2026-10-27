package com.knowflow.interview;
import com.knowflow.interview.core.*;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.mapper.*;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContentStudioFollowupBankTest {
 QuizQuestion q(String key,String title,String answer,String tail){QuizQuestion q=new QuizQuestion();q.setId((long)key.hashCode());q.setCanonicalKey(key);q.setTitle(title);q.setAnswer(answer);q.setTopicCode("JAVA_COLLECTION");q.setCategoryId(1L);q.setExplanation(tail==null?null:"正文\n来源与审核原记录：\n"+tail);return q;}
 ContentStudioInterviewBank bank(List<QuizQuestion> rows){QuizQuestionMapper mapper=mock(QuizQuestionMapper.class);when(mapper.selectList(any())).thenReturn(rows);DocumentAccessPolicy access=mock(DocumentAccessPolicy.class);when(access.canReadCategory(any(),eq(7L))).thenReturn(true);when(access.canReadCategory(eq(2L),eq(7L))).thenReturn(false);return new ContentStudioInterviewBank(mapper,access,mock(DocDocumentMapper.class));}
 @Test void preservesThreeLevelsReferenceAnswersOrderAndFrozenSnapshot(){
  QuizQuestion root=q("root","main","main answer","{\"followupTree\":[{\"level\":1,\"question\":\"first\",\"referenceAnswer\":\"answer1\"},{\"level\":2,\"question\":\"second\",\"referenceAnswer\":\"answer2\"},{\"level\":3,\"question\":\"third\",\"referenceAnswer\":\"answer3\"}]}");
  var snapshot=bank(List.of(root)).tracks(7L).get(0).questions().get(0);
  assertEquals(List.of("first","second","third"),snapshot.followups());assertEquals("main answer",snapshot.referencePoints().get(0));assertEquals(List.of("第1层追问：first\n参考答案：answer1","第2层追问：second\n参考答案：answer2","第3层追问：third\n参考答案：answer3"),snapshot.referencePoints().subList(1,4));
  root.setAnswer("changed");root.setExplanation("changed");assertEquals("main answer",snapshot.referencePoints().get(0));assertEquals("third",snapshot.followups().get(2));assertThrows(UnsupportedOperationException.class,()->snapshot.followups().add("injected"));
 }
 @Test void resolvesLegacyChildrenOnlyFromReadableUniqueBank(){
  var root=q("root","main","answer","{\"followupTree\":{\"parentKey\":null,\"childKeys\":[\"child\",\"private\",\"missing\"]}}");var child=q("child","child prompt","child answer",null);var hidden=q("private","secret","secret answer",null);hidden.setCategoryId(2L);
  var snapshot=bank(List.of(root,child,hidden)).tracks(7L).get(0).questions().get(0);assertEquals(List.of("child prompt"),snapshot.followups());assertEquals("第1层追问：child prompt\n参考答案：child answer",snapshot.referencePoints().get(1));
 }
 @Test void malformedDeclaredMetadataFailsClearly(){var error=assertThrows(BusinessException.class,()->bank(List.of(q("root","main","answer","{bad json"))).tracks(7L));assertEquals(500,error.getCode());assertTrue(error.getMessage().contains("追问"));}
 @Test void missingMetadataRemainsEmptyAndFiveFollowupLimitApplies(){assertTrue(bank(List.of(q("plain","plain","answer",null))).tracks(7L).get(0).questions().get(0).followups().isEmpty());String tail="{\"followupTree\":["+String.join(",",Collections.nCopies(6,"{\"question\":\"same\",\"level\":1}"))+"]}";assertEquals(5,bank(List.of(q("limit","limit","answer",tail))).tracks(7L).get(0).questions().get(0).followups().size());}
}
