package com.knowflow.learning.actions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.Task;
import com.knowflow.mapper.TaskMapper;
import com.knowflow.dto.TaskDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.TaskService;
import com.knowflow.service.LearningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static com.knowflow.learning.actions.PlanModels.*;
/** Rules create drafts; only this authenticated confirmation transaction performs writes. */
@Service @RequiredArgsConstructor @Slf4j
public class DailyLearningPlanService {
 private final LearningWriteGuard guard;
 private final LearningActionDraftMapper drafts;private final LearningContextService context;private final TaskService taskService;
 private final TaskMapper tasks;private final LearningService learning;private final ObjectMapper json;private final PlanClock clock;
 private final LearningPlanAiEnhancer enhancer;private final com.knowflow.mapper.LearningTaskMapper legacyTasks;
 @org.springframework.beans.factory.annotation.Autowired(required=false) private com.knowflow.learning.adaptive.SmartPlanSupport smart;
 @Value("${jwt.secret}") private String tokenSecret;
 public record Stored(Request request,List<Item> tasks,List<String> warnings,String source,String action,Integer targetStatus,Map<String,String> revisions,List<TaskChange> changes){
  public Stored(Request r,List<Item> rows,List<String> warnings,String source,String action,Integer status,Map<String,String> revisions){this(r,rows,warnings,source,action,status,revisions,List.of());}
  public Stored(Request r,List<Item> rows,List<String> warnings,String source,String action,Integer status){this(r,rows,warnings,source,action,status,Map.of());}
 }
 public Draft preview(Long user,Request request){return save(user,buildPlan(user,request));}
 private Stored buildPlan(Long user,Request request){
  Request r=normalize(request);var ctx=context.read(user,r);var rows=new DailyPlanRule().generate(r,ctx);List<String> warnings=new ArrayList<>(ctx.missingData().stream().map(x->"缺少："+x).toList());
  if(ctx.existingTasks()>0)warnings.add("已有"+ctx.existingTasks()+"项今日待办；确认将追加，不会覆盖或删除原任务。");
  var improved=enhancer.enhance(user,r,rows);warnings.addAll(improved.warnings());
  if(Boolean.TRUE.equals(r.getIncludeInterview())&&rows.stream().noneMatch(x->x.type().equals("INTERVIEW")))warnings.add("预算不足，本计划未包含模拟面试。");
  return new Stored(r,improved.tasks(),List.copyOf(warnings),improved.source(),"CREATE",null);
 }
 public Draft previewChanges(Long user,Request request){return save(user,buildChanges(user,request));}
 private Stored buildChanges(Long user,Request request){
  Request r=normalize(request);if(smart!=null)r.setSmartReorder(true);if(Boolean.TRUE.equals(r.getSmartReorder())){if(smart==null)throw new BusinessException(400,"智能重排未启用");var result=smart.build(user,r,todayTasks(user,r),this::metadata,this::priority);return new Stored(r,result.items(),result.warnings(),"RULE","UPDATE",null,Map.of(),result.changes());}Request selection=r;
  if(r.getInstruction()!=null&&r.getInstruction().contains("今天没完成")){selection=new Request();selection.setDate(clock.now().atZone(LearningContextService.zone(r.getZoneId())).toLocalDate());}
  var owned=todayTasks(user,selection);if(owned.isEmpty())return buildPlan(user,r);
  int max=Math.min(owned.size(),Math.max(1,r.getAvailableMinutes()/5));List<Item> items=new ArrayList<>();int base=r.getAvailableMinutes()/max,remain=r.getAvailableMinutes()%max;
  for(int i=0;i<max;i++){var t=owned.get(i);var old=metadata(t);items.add(new Item("task:"+t.getId(),t.getTitle(),old==null?"THEORY":old.type(),base+(i<remain?1:0),priority(t),old==null?"TOOL":old.resourceType(),old==null?null:old.resourceId(),old==null?null:old.pathId(),old==null?null:old.knowledgeId(),"调整本人今日待办，保留原任务与学习记录",old==null?"按任务说明手动确认完成":old.completionCondition(),t.getId(),"TASK",t.getStatus()));}
  return new Stored(r,List.copyOf(items),List.of("这是调整草案，确认前不修改任务；从草案删除某项不会删除已保存任务。"),"RULE","UPDATE",null);
 }
 public Draft previewStatus(Long user,Long id,String source,Integer target,Request req){
  if(id==null||id<=0||target==null||target<0||target>1)throw new BusinessException(400,"无效任务状态");Request r=normalize(req);Item item;
  if("LEARNING".equals(source)){
   var t=learning.getTaskList(user).stream().filter(x->x.getId().equals(id)).findFirst().orElseThrow(()->new BusinessException(404,"任务不存在"));
   item=new Item("learning:"+id,t.getTitle(),"STATUS",1,"MEDIUM","TOOL",null,null,null,"用户明确请求修改任务状态；不代表知识掌握","手动确认任务状态",id,"LEARNING",t.getStatus());
  }else if("TASK".equals(source)){
   var t=ownedTask(user,id);var old=metadata(t);item=new Item("task:"+id,t.getTitle(),"STATUS",1,priority(t),old==null?"TOOL":old.resourceType(),old==null?null:old.resourceId(),old==null?null:old.pathId(),old==null?null:old.knowledgeId(),"用户明确请求修改任务状态；不代表知识掌握","手动确认任务状态",id,"TASK",t.getStatus());
  }else throw new BusinessException(400,"无效任务来源");
  return save(user,new Stored(r,List.of(item),List.of("只更新任务状态，不自动生成掌握度、答题或练习通过证据。"),"RULE","STATUS",target));
 }
 public List<Task> todayTasks(Long user,Request r){
  return tasks.selectList(new LambdaQueryWrapper<Task>().eq(Task::getUserId,user).le(Task::getScheduledDate,r.getDate()).eq(Task::getStatus,0).orderByAsc(Task::getSortOrder).orderByAsc(Task::getId).last("LIMIT 30"));
 }
 private Draft save(Long user,Stored stored){
  var d=new LearningActionDraft();d.setId(UUID.randomUUID().toString());d.setUserId(user);d.setVersion(1);d.setState("PREVIEW");d.setCreateTime(clock.now());d.setExpiresAt(clock.now().plusSeconds(1800));d.setPayload(write(freeze(user,stored)));drafts.insert(d);return view(d);
 }
 private Stored freeze(Long user,Stored stored){Map<String,String> revisions=new HashMap<>();for(var item:stored.tasks())if(item.existingTaskId()!=null)revisions.put(item.key(),existingRevision(user,item));for(var change:changes(stored))if(!revisions.containsKey("task:"+change.taskId()))revisions.put("task:"+change.taskId(),digest(write(ownedTask(user,change.taskId()))));return new Stored(stored.request(),stored.tasks(),stored.warnings(),stored.source(),stored.action(),stored.targetStatus(),Map.copyOf(revisions),changes(stored));}
 private String existingRevision(Long user,Item item){
  if("TASK".equals(item.existingSource()))return digest(write(ownedTask(user,item.existingTaskId())));
  var task=learning.getTaskList(user).stream().filter(t->t.getId().equals(item.existingTaskId())).findFirst().orElseThrow(()->new BusinessException(404,"任务不存在"));return digest(write(task));
 }
 @Transactional public Draft regenerate(Long user,String id,Request input){var d=drafts.lockOwned(id,user);requireOwned(d);if("CONFIRMED".equals(d.getState()))throw new BusinessException(409,"已保存计划不能重新生成覆盖");var old=read(d.getPayload(),Stored.class);if("STATUS".equals(old.action()))throw new BusinessException(400,"状态确认草案不支持重新生成");if(smartPlan(old)){if(input==null)input=new Request();input.setSmartReorder(true);input.setUseAi(false);}var stored="UPDATE".equals(old.action())?buildChanges(user,input):buildPlan(user,input);d.setVersion(d.getVersion()+1);d.setPayload(write(freeze(user,stored)));d.setState("PREVIEW");d.setExpiresAt(clock.now().plusSeconds(1800));drafts.updateById(d);return view(d);}
 public Draft get(Long user,String id){var d=owned(user,id);var s=read(d.getPayload(),Stored.class);if(smartPlan(s))validateSmartSources(user,s);return view(d);}
 @Transactional public void cancel(Long user,String id){var d=drafts.lockOwned(id,user);requireOwned(d);if("CONFIRMED".equals(d.getState()))throw new BusinessException(409,"计划已经确认，不能撤销已保存任务");d.setState("CANCELLED");drafts.updateById(d);}
 @Transactional(rollbackFor=Exception.class)
 public Draft confirm(Long user,String id,ConfirmRequest request){
  var d=drafts.lockOwned(id,user);requireOwned(d);if(request==null||request.getVersion()==null||!request.getVersion().equals(d.getVersion()))throw new BusinessException(409,"草案版本已变化");
  if(request.getConfirmationToken()==null||!MessageDigest.isEqual(token(d).getBytes(StandardCharsets.UTF_8),request.getConfirmationToken().getBytes(StandardCharsets.UTF_8)))throw new BusinessException(403,"缺少有效确认令牌");
  if(request.getIdempotencyKey()==null||!request.getIdempotencyKey().matches("[A-Za-z0-9_-]{8,80}"))throw new BusinessException(400,"无效幂等键");
  var frozen=read(d.getPayload(),Stored.class);if(smartPlan(frozen))validateSmartSources(user,frozen);
  String hash=digest(write(request.getTasks()));
  if("CONFIRMED".equals(d.getState())){if(!request.getIdempotencyKey().equals(d.getIdempotencyKey())||!hash.equals(d.getRequestHash()))throw new BusinessException(409,"计划已创建，不能使用不同内容重复确认");return view(d);}
  if(!"PREVIEW".equals(d.getState()))throw new BusinessException(409,"草案已取消");if(!clock.now().isBefore(d.getExpiresAt()))throw new BusinessException(410,"草案已过期，请重新生成");
  Stored s=read(d.getPayload(),Stored.class);if(smart!=null&&"UPDATE".equals(s.action())&&!smartPlan(s))throw new BusinessException(409,"旧调整草案需重新预览以校验任务保护");var selected=validateEdits(s,request.getTasks());
  // STATUS LEARNING也先锁owner，随后才取任务锁，与LearningService保持顺序。
  guard.lock(user);
  // Acquire existing task locks in stable order; prevent lost updates after a stale preview.
  selected.stream().filter(i->i.existingTaskId()!=null&&"TASK".equals(i.existingSource())).sorted(Comparator.comparing(Item::existingTaskId)).forEach(i->{if(tasks.lockOwned(i.existingTaskId(),user)==null)throw new BusinessException(404,"任务不存在");});
  selected.stream().filter(i->i.existingTaskId()!=null&&"LEARNING".equals(i.existingSource())).sorted(Comparator.comparing(Item::existingTaskId)).forEach(i->{if(legacyTasks.lockOwned(i.existingTaskId(),user)==null)throw new BusinessException(404,"任务不存在");});
  for(var i:selected)if(i.existingTaskId()!=null&&!Objects.equals(s.revisions().get(i.key()),existingRevision(user,i)))throw new BusinessException(409,"任务已发生变化，请重新预览");List<TaskRef> result=new ArrayList<>();int order=0;
  for(var item:selected){
   if(!"STATUS".equals(s.action()))context.validateResource(item,user);
   Long taskId;
   if("STATUS".equals(s.action())){
    taskId=item.existingTaskId();verifyExisting(user,item);
    if("TASK".equals(item.existingSource()))taskService.setStatus(user,taskId,s.targetStatus());else {
     // Chat确认也使用同一行锁/主事务状态合同；手动活动不生成知识证明。
     learning.updateTaskStatus(taskId,user,s.targetStatus());
    }
    result.add(new TaskRef(taskId,item.existingSource(),item.title(),s.targetStatus()));continue;
   }
   if("CREATE".equals(s.action())){
    Long duplicate=guard.task(user,s.request().getDate(),guard.identity(item));
    if(duplicate!=null){var existing=ownedTask(user,duplicate);result.add(new TaskRef(existing.getId(),"TASK",existing.getTitle(),existing.getStatus()));continue;}
   }
   if(smartPlan(s)&&s.request().getProtectedTaskIds().contains(item.existingTaskId())){var protectedTask=ownedTask(user,item.existingTaskId());result.add(new TaskRef(protectedTask.getId(),"TASK",protectedTask.getTitle(),protectedTask.getStatus()));continue;}
   TaskDTO dto=new TaskDTO();dto.setTitle(item.title());dto.setScheduledDate(s.request().getDate());dto.setDueDate(smartPlan(s)?ownedTask(user,item.existingTaskId()).getDueDate():s.request().getDate());dto.setImportant(item.priority().equals("LOW")?0:1);dto.setUrgent(item.priority().equals("HIGH")?1:0);if(smartPlan(s)){var prior=ownedTask(user,item.existingTaskId());dto.setImportant(prior.getImportant());dto.setUrgent(prior.getUrgent());int slot=smart.freeOrder(user,s.request(),order);dto.setSortOrder(slot);order=slot+1;}else dto.setSortOrder(order++);
   // Durations remain metadata: no fake start/finish times or completed progress.
   if("UPDATE".equals(s.action())){verifyExisting(user,item);taskId=item.existingTaskId();taskService.updateTask(user,taskId,dto);}else{dto.setNotes(item.reason()+"\n完成条件："+item.completionCondition());taskId=taskService.createTask(user,dto);}
   Task persisted=ownedTask(user,taskId);String metadata=guard.taskMetadata(persisted,item);persisted.setLearningPlanRef(d.getId());persisted.setLearningMetadata(metadata);if(tasks.updateById(persisted)!=1)throw new BusinessException(409,"任务写入失败");
   if("UPDATE".equals(s.action()))guard.moveTask(user,taskId,s.request().getDate());
   if("CREATE".equals(s.action()))guard.rememberTask(user,s.request().getDate(),guard.identity(item),taskId);
   result.add(new TaskRef(taskId,"TASK",item.title(),persisted.getStatus()));
  }
  if(smartPlan(s))smart.deferred(user,s.request(),changes(s),s.revisions(),t->digest(write(t)),this::metadata,result);
  d.setState("CONFIRMED");d.setResultJson(write(result));d.setIdempotencyKey(request.getIdempotencyKey());d.setRequestHash(hash);
  d.setPayload(write(new Stored(s.request(),selected,s.warnings(),s.source(),s.action(),s.targetStatus(),s.revisions(),changes(s))));if(drafts.updateById(d)!=1)throw new BusinessException(409,"确认记录写入失败");
  log.info("[LearningAction] confirmed userId={} planId={} action={} count={}",user,d.getId(),s.action(),result.size());return view(d);
 }
 private void verifyExisting(Long user,Item item){
  if("TASK".equals(item.existingSource())){var t=ownedTask(user,item.existingTaskId());if(!Objects.equals(t.getStatus(),item.previousStatus()))throw new BusinessException(409,"任务状态已变化，请重新预览");}
  else if("LEARNING".equals(item.existingSource())){var t=learning.getTaskList(user).stream().filter(x->x.getId().equals(item.existingTaskId())).findFirst().orElseThrow(()->new BusinessException(404,"任务不存在"));if(!Objects.equals(t.getStatus(),item.previousStatus()))throw new BusinessException(409,"任务状态已变化，请重新预览");}
  else throw new BusinessException(400,"无效任务来源");
 }
 private List<Item> validateEdits(Stored s,List<EditedItem> edits){
  if(edits==null||edits.isEmpty()||edits.size()>20)throw new BusinessException(400,"请选择1至20项任务");Map<String,Item> source=new HashMap<>();for(var i:s.tasks())source.put(i.key(),i);Set<String> used=new HashSet<>();List<Item> out=new ArrayList<>();int total=0;
  for(var e:edits){if(e==null||!used.add(e.key())||!source.containsKey(e.key()))throw new BusinessException(400,"候选任务无效");if(e.priority()==null||e.title()==null||e.title().isBlank()||e.title().length()>200||e.estimatedMinutes()<1||e.estimatedMinutes()>720||!Set.of("HIGH","MEDIUM","LOW").contains(e.priority()))throw new BusinessException(400,"标题、时长或优先级无效");var i=source.get(e.key());if("STATUS".equals(s.action())&&(!Objects.equals(e.title(),i.title())||e.estimatedMinutes()!=i.estimatedMinutes()||!Objects.equals(e.priority(),i.priority())))throw new BusinessException(400,"状态确认不能修改任务标题、时长或优先级");total+=e.estimatedMinutes();out.add(new Item(i.key(),e.title().trim(),i.type(),e.estimatedMinutes(),e.priority(),i.resourceType(),i.resourceId(),i.pathId(),i.knowledgeId(),i.reason(),i.completionCondition(),i.existingTaskId(),i.existingSource(),i.previousStatus()));}
  if(smartPlan(s))smart.validateExact(s.tasks(),out);if(total>s.request().getAvailableMinutes())throw new BusinessException(400,"任务总时长超出预算");if("STATUS".equals(s.action())&&out.size()!=1)throw new BusinessException(400,"状态变更只能确认一个任务");return List.copyOf(out);
 }
 public Request normalize(Request input){Request r=input==null?new Request():input;var zone=LearningContextService.zone(r.getZoneId());r.setZoneId(zone.getId());if(r.getDate()==null)r.setDate(clock.now().atZone(zone).toLocalDate());if(r.getAvailableMinutes()==null)r.setAvailableMinutes(120);if(r.getAvailableMinutes()<1||r.getAvailableMinutes()>720)throw new BusinessException(400,"学习时长必须为1至720分钟");if(r.getFocusTopics()==null)r.setFocusTopics(List.of());if(r.getFocusTopics().size()>8||r.getFocusTopics().stream().anyMatch(x->x==null||x.isBlank()||x.length()>80))throw new BusinessException(400,"重点主题过多或过长");if(r.getInstruction()!=null&&r.getInstruction().length()>2000)throw new BusinessException(400,"指令过长");if(r.getTaskMinutes()==null)r.setTaskMinutes(Map.of());if(r.getTaskMinutes().size()>100||r.getTaskMinutes().entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey()<1||e.getValue()==null||e.getValue()<1||e.getValue()>720))throw new BusinessException(400,"任务时长设置无效");return r;}
 public record Settings(String revision,boolean locked,int estimatedMinutes){}
 @Transactional public void smartSettings(Long user,Long id,Settings input){
  if(smart==null)throw new BusinessException(404,"功能未启用");guard.lock(user);var t=tasks.lockOwned(id,user);if(t==null)throw new BusinessException(404,"任务不存在");if(input==null||!Objects.equals(input.revision(),taskRevision(t)))throw new BusinessException(409,"任务已变化，请刷新");if(!Integer.valueOf(0).equals(t.getStatus()))throw new BusinessException(409,"不能调整已完成任务");if(input.estimatedMinutes()<1||input.estimatedMinutes()>720)throw new BusinessException(400,"预计时长必须为1至720分钟");var old=metadata(t);if(old!=null)context.validateResource(old,user);if(t.getStartTime()!=null&&input.estimatedMinutes()!=smart.minutes(t,old))throw new BusinessException(409,"不能改变定时任务时长");
  var item=new Item("task:"+id,t.getTitle(),old==null?"THEORY":old.type(),input.estimatedMinutes(),priority(t),old==null?"TOOL":old.resourceType(),old==null?null:old.resourceId(),old==null?null:old.pathId(),old==null?null:old.knowledgeId(),old==null?"用户设置预计时长":old.reason(),old==null?"手动完成，不产生知识掌握证明":old.completionCondition(),id,"TASK",t.getStatus());
  try{var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(guard.taskMetadata(t,item));node.put("planningLocked",input.locked());t.setLearningMetadata(write(node));if(tasks.updateById(t)!=1)throw new BusinessException(409,"任务已变化");}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new BusinessException(500,"任务元数据损坏");}
 }
 public List<Map<String,Object>> smartTasks(Long user,Request request){if(smart==null)throw new BusinessException(404,"功能未启用");var r=normalize(request);var rows=tasks.selectList(new LambdaQueryWrapper<Task>().eq(Task::getUserId,user).le(Task::getScheduledDate,r.getDate()).orderByAsc(Task::getSortOrder).orderByAsc(Task::getId).last("LIMIT 100"));var out=new ArrayList<Map<String,Object>>();for(var t:rows){var m=metadata(t);if(m!=null)context.validateResource(m,user);var row=new LinkedHashMap<String,Object>();row.put("id",t.getId());row.put("title",t.getTitle());row.put("status",t.getStatus());row.put("scheduledDate",t.getScheduledDate());row.put("dueDate",t.getDueDate());row.put("estimatedMinutes",smart.minutes(t,m));row.put("durationSource",m==null?"DEFAULT_ESTIMATE":"TASK_METADATA");row.put("locked",smart.locked(t));row.put("revision",taskRevision(t));out.add(row);}return out;}
 public Draft getSmart(Long user,String id){var d=owned(user,id);var s=read(d.getPayload(),Stored.class);if(!smartPlan(s))throw new BusinessException(404,"智能调整草案不存在");validateSmartSources(user,s);return view(d);}
 private void validateSmartSources(Long user,Stored s){for(var i:s.tasks())context.validateResource(i,user);for(var c:changes(s)){var m=metadata(ownedTask(user,c.taskId()));if(m!=null)context.validateResource(m,user);}}
 public List<Draft> smartHistory(Long user,Request request){var r=normalize(request);var out=new ArrayList<Draft>();for(var d:drafts.selectList(new LambdaQueryWrapper<LearningActionDraft>().eq(LearningActionDraft::getUserId,user).orderByDesc(LearningActionDraft::getCreateTime).last("LIMIT 100"))){var s=read(d.getPayload(),Stored.class);if(!smartPlan(s)||!r.getDate().equals(s.request().getDate()))continue;try{validateSmartSources(user,s);out.add(view(d));}catch(BusinessException e){if(!Set.of(403,404,409).contains(e.getCode()))throw e;out.add(new Draft(d.getId(),d.getVersion(),d.getExpiresAt(),"SOURCE_UNAVAILABLE",s.source(),s.action(),s.request().getDate(),s.request().getZoneId(),s.request().getAvailableMinutes(),List.of(),List.of("原学习来源已不可用，隐藏清单内容"),null,List.of(),null,null,List.of()));}}return out;}
 private boolean smartPlan(Stored s){return Boolean.TRUE.equals(s.request().getSmartReorder())&&"UPDATE".equals(s.action());}
 private List<TaskChange> changes(Stored s){return s.changes()==null?List.of():s.changes();}
 public String taskRevision(Task t){return digest(write(t));}
 private LearningActionDraft owned(Long user,String id){var d=drafts.selectById(id);if(d==null||!Objects.equals(d.getUserId(),user))throw new BusinessException(404,"草案不存在");return d;}
 private void requireOwned(LearningActionDraft d){if(d==null)throw new BusinessException(404,"草案不存在");}
 private Task ownedTask(Long user,Long id){var t=tasks.selectById(id);if(t==null||!Objects.equals(t.getUserId(),user))throw new BusinessException(404,"任务不存在");return t;}
 public Item metadata(Task task){return task.getLearningMetadata()==null?null:read(task.getLearningMetadata(),Item.class);}
 private String priority(Task t){return Integer.valueOf(1).equals(t.getUrgent())?"HIGH":Integer.valueOf(1).equals(t.getImportant())?"MEDIUM":"LOW";}
 private Draft view(LearningActionDraft d){Stored s=read(d.getPayload(),Stored.class);List<TaskRef> ids=d.getResultJson()==null?List.of():readRefs(d.getResultJson());String state=d.getState();if("PREVIEW".equals(state)&&!clock.now().isBefore(d.getExpiresAt()))state="EXPIRED";return new Draft(d.getId(),d.getVersion(),d.getExpiresAt(),state,s.source(),s.action(),s.request().getDate(),s.request().getZoneId(),s.request().getAvailableMinutes(),s.tasks(),s.warnings(),"PREVIEW".equals(state)?token(d):null,ids,s.targetStatus(),s.request(),changes(s));}
 private String token(LearningActionDraft d){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((d.getId()+":"+d.getUserId()+":"+d.getVersion()).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("confirmation signing unavailable");}}
 private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
 public String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new BusinessException(500,"计划序列化失败");}}
 private <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception ex){throw new BusinessException(500,"计划记录损坏");}}
 private List<TaskRef> readRefs(String value){try{return json.readValue(value,new TypeReference<List<TaskRef>>(){});}catch(Exception ex){throw new BusinessException(500,"计划结果损坏");}}
}
