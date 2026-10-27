package com.knowflow.learning.actions;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.ChatMessage;
import com.knowflow.mapper.*;
import com.knowflow.service.LearningService;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.regex.*;
import static com.knowflow.learning.actions.PlanModels.*;
/** Server-side natural language dispatcher. Learning tools never inherit coding-agent privileges. */
@Service @RequiredArgsConstructor
public class ChatLearningActionService {
 private final LearningActionToolExecutor tools;private final com.fasterxml.jackson.databind.ObjectMapper json;
 private final DailyLearningPlanService plans;private final LearningService learning;
 private final ChatConversationMapper conversations;private final ChatMessageMapper messages;private final JdbcTemplate jdbc;
 @Transactional public ChatResponse handle(Long user,ChatRequest input){
  if(input==null||input.message()==null)throw new BusinessException(400,"缺少消息");
  var conversation=conversations.selectById(input.conversationId());if(conversation==null||!Objects.equals(conversation.getUserId(),user))throw new BusinessException(404,"对话不存在");
  String m=input.message().trim();String intent=intent(m);if(intent==null)return new ChatResponse(false,"",null,List.of(),List.of(),input.conversationId());
  if(m.length()>2000)throw new BusinessException(400,"学习行动指令过长");
  Request r=parse(m,input.preferences());Draft draft=null;List<TaskRef> tasks=new ArrayList<>(),choices=new ArrayList<>();String text;
  switch(intent){
   case "CONTEXT"->{
    var ctx=(PlanModels.Context)tools.execute("getLearningContext",json.valueToTree(r),user);
    StringBuilder answer=new StringBuilder("根据本人真实记录：\n");
    answer.append("已到期闪卡（最多统计50张）：").append(ctx.facts().getOrDefault("dueFlashcards",0)).append("；有有效证据的知识记录：").append(ctx.facts().getOrDefault("masteryRecordsWithEvidence",0)).append("。\n");
    for(var item:new DailyPlanRule().generate(r,ctx).stream().limit(3).toList())answer.append("- ").append(item.title()).append("；").append(item.reason()).append("；预计").append(item.estimatedMinutes()).append("分钟；完成标准：").append(item.completionCondition()).append("\n");
    for(String missing:ctx.missingData())answer.append("缺少：").append(missing).append("。\n");
    answer.append("此查询未创建或修改任务；可在学习中心预览并确认今日计划。");text=answer.toString();
   }
   case "LIST"->{tasks=today(user,r);text=tasks.isEmpty()?"今天没有待完成任务。":"今天还有"+tasks.size()+"项真实待办任务。";}
   case "STATUS"->{
    var all=today(user,r);String title=m.replaceFirst(".*?(?:已经|已)?完成(?:了)?","").replaceAll("[。！!？?]$","").trim();String needle=title.replace("复习","").replace("任务","").trim();
    var matched=input.selectedTaskId()!=null?all.stream().filter(t->t.id().equals(input.selectedTaskId())&&Objects.equals(t.source(),input.selectedTaskSource())).toList():all.stream().filter(t->!needle.isBlank()&&(t.title().contains(title)||t.title().contains(needle))).toList();
    if(matched.size()!=1){choices=matched;text=matched.isEmpty()?"未找到唯一匹配的今日任务，请指定任务名称。":"找到多个同名或相关任务，请先选择要更新的那一项。";}
    else{var target=matched.get(0);draft=(Draft)tools.execute("updateTaskStatus",json.valueToTree(Map.of("taskId",target.id(),"source",target.source(),"status",1,"zoneId",r.getZoneId(),"date",r.getDate().toString())),user);text="请确认将「"+target.title()+"」标记完成。确认前不会更新任务，也不会自动认定知识已掌握。";}
   }
   case "UPDATE"->{draft=(Draft)tools.execute("previewTaskChanges",json.valueToTree(r),user);text="已生成调整草案，确认前不会修改或删除原任务。";}
   default->{draft=(Draft)tools.execute("generateDailyPlan",json.valueToTree(r),user);text="已生成学习任务草案，尚未创建任务。请检查并确认。";}
  }
  Map<String,Object> payload=new LinkedHashMap<>();payload.put("kind","KNOWFLOW_LEARNING_ACTION");payload.put("text",text);payload.put("planId",draft==null?null:draft.planId());payload.put("tasks",tasks);payload.put("choices",choices);
  conversations.initializeTitle(input.conversationId(),user,m.substring(0,Math.min(50,m.length())));
  persist(user,input.conversationId(),"user",m);persist(user,input.conversationId(),"assistant",plans.write(payload));
  jdbc.update("UPDATE chat_conversation SET message_count=COALESCE(message_count,0)+2,last_message=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted=0",text,input.conversationId(),user);
  return new ChatResponse(true,text,draft==null?null:draft.planId(),List.copyOf(tasks),List.copyOf(choices),input.conversationId());
 }
 private List<TaskRef> today(Long user,Request r){List<TaskRef> result=new ArrayList<>();for(var t:plans.todayTasks(user,r))result.add(new TaskRef(t.getId(),"TASK",t.getTitle(),t.getStatus()));for(var t:learning.getTaskList(user)){if(t.getStatus()!=null&&t.getStatus()==1)continue;if(t.getDeadline()!=null&&t.getDeadline().toLocalDate().isAfter(r.getDate()))continue;result.add(new TaskRef(t.getId(),"LEARNING",t.getTitle(),t.getStatus()));if(result.size()>=30)break;}return result.size()>30?List.copyOf(result.subList(0,30)):List.copyOf(result);}
 private void persist(Long user,Long conversation,String role,String content){ChatMessage m=new ChatMessage();m.setUserId(user);m.setConversationId(conversation);m.setRole(role);m.setContent(content);m.setTokenCount(content.length());messages.insert(m);}
 public static String intent(String text){
  if(text.matches("(?s).*(?:不要生成|无需生成|不用安排|不要安排).*"))return null;
  if(text.matches("(?s).*(?:今天应该学什么|今日应该学什么|今天学什么|哪里.*薄弱|做错.*(?:CAS|题)|闪卡.*复习|哪些闪卡|下一步怎么学).*"))return "CONTEXT";
  if(text.matches("(?s).*(?:今天|今日).*(?:还有|有什么|查询|查看).*(?:任务|计划).*"))return "LIST";
  if(text.matches("(?s).*(?:我已|我已经|已经完成|完成了).+"))return "STATUS";
  if(text.matches("(?s).*(?:重新安排|重新规划|调整计划|只剩|没完成的任务调整到).+"))return "UPDATE";
  if(text.matches("(?s).*(?:生成|安排|制定|规划).*(?:学习|今日|今天).*(?:任务|计划|小时|分钟).*"))return "CREATE";
  return null;
 }
 private Request parse(String text,Request preferences){Request r=preferences==null?new Request():preferences;
  Matcher minutes=Pattern.compile("(\\d+(?:\\.\\d+)?|[一二两三四五六七八九十]+|半)\\s*(小时|分钟|hours?|minutes?)",Pattern.CASE_INSENSITIVE).matcher(text);
  if(minutes.find()){String value=minutes.group(1);double n=switch(value){case "一"->1;case "二","两"->2;case "三"->3;case "四"->4;case "五"->5;case "六"->6;case "七"->7;case "八"->8;case "九"->9;case "十"->10;case "半"->0.5;default->{try{yield Double.parseDouble(value);}catch(NumberFormatException ex){throw new BusinessException(400,"无法识别学习时长");}}};r.setAvailableMinutes((int)Math.round(n*(minutes.group(2).matches("小时|(?i)hours?")?60:1)));}
  if(r.getFocusTopics()==null||r.getFocusTopics().isEmpty()){List<String> focus=new ArrayList<>();for(String k:List.of("Java并发","Java 并发","Kafka","RAG","Spring","JVM","Redis","MySQL","算法","Java","AI"))if(text.toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT))&&!focus.stream().anyMatch(f->f.contains(k)))focus.add(k);r.setFocusTopics(focus.stream().limit(8).toList());}
  if(text.contains("不含练习")||text.contains("不要练习")||text.contains("只学理论"))r.setIncludePractice(false);
  if(text.contains("不含面试")||text.contains("不要面试")||text.contains("只学理论"))r.setIncludeInterview(false);
  r.setInstruction(text);r=plans.normalize(r);if(text.contains("明天"))r.setDate(r.getDate().plusDays(1));return r;
 }
}
