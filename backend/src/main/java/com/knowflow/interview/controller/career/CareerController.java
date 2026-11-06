package com.knowflow.interview.controller.career;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

import com.knowflow.interview.service.career.CareerDesignService;
import com.knowflow.interview.service.career.CareerJobPipelineService;
import com.knowflow.interview.service.career.CareerLinkImportService;
import com.knowflow.interview.service.career.CareerParsingService;
import com.knowflow.interview.service.career.CareerRecordService;
import com.knowflow.interview.service.career.CareerToolboxService;
import com.knowflow.interview.service.career.CareerUploadService;
import com.knowflow.interview.service.career.CareerWorkflowService;
import com.knowflow.interview.service.career.CareerWorkspaceService;
import com.knowflow.interview.service.career.insights.CareerInsightsService;
/** 简历、岗位和匹配仅接受服务端身份，资源归属由Service再次校验。 */
@RestController @RequestMapping("/api/interview/career") @RequiredArgsConstructor
public class CareerController {
 private final CareerRecordService records;
 private final CareerWorkflowService workflow;
 private final CareerParsingService parsing;
 private final CareerUploadService uploads;
 private final CareerWorkspaceService workspace;
 private final CareerJobPipelineService pipelines;
 private final CareerLinkImportService links;
 private final CareerToolboxService toolbox;
 private final CareerDesignService designs;
 private final com.knowflow.interview.service.career.insights.CareerInsightsService insights;
 public record JobStatus(String status,String note){}
 public record Polish(int version,boolean allowModelProcessing,List<String> quotes){}
 public record LinkImport(String url){}
 public record Regenerate(String seed){}
 public record AtsCheck(long resumeId,Integer version,Long jobId,Integer jobVersion){}
 public record ContentCheck(long resumeId,Integer version){}
 public record JdOptimize(long resumeId,Integer resumeVersion,long jobId,Integer jobVersion){}
 @GetMapping("/dashboard") public Result<?> dashboard(){return Result.success(workspace.dashboard(SecurityUtils.getCurrentUserId()));}
 @GetMapping("/overview") public Result<?> overview(){return Result.success(workspace.overview(SecurityUtils.getCurrentUserId()));}
 @GetMapping("/insights") public Result<?> insights(){return Result.success(insights.insights(SecurityUtils.getCurrentUserId()));}
 @GetMapping("/jobs") public Result<?> jobs(@RequestParam(required=false) String status,@RequestParam(required=false) String keyword,@RequestParam(required=false) String sort){return Result.success(workspace.listJobs(SecurityUtils.getCurrentUserId(),status,keyword,sort));}
 @GetMapping("/jobs/{id}/events") public Result<?> events(@PathVariable long id){return Result.success(pipelines.events(SecurityUtils.getCurrentUserId(),id));}
 @GetMapping("/jobs/{id}/pipeline") public Result<?> pipeline(@PathVariable long id){return Result.success(pipelines.get(SecurityUtils.getCurrentUserId(),id));}
 @PutMapping("/jobs/{id}/pipeline") public Result<?> updatePipeline(@PathVariable long id,@RequestBody JobStatus body){if(body==null)throw new BusinessException(400,"缺少岗位状态");return Result.success(pipelines.update(SecurityUtils.getCurrentUserId(),id,body.status(),body.note()));}
 @PostMapping("/records/JOB/import-url") public Result<?> importUrl(@RequestBody LinkImport body){if(body==null)throw new BusinessException(400,"缺少岗位链接");return Result.success(links.importUrl(SecurityUtils.getCurrentUserId(),body.url()));}
 @PostMapping("/records/{id}/polish") public Result<?> polish(@PathVariable long id,@RequestBody Polish body){if(body==null)throw new BusinessException(400,"缺少润色参数");return Result.success(parsing.polish(SecurityUtils.getCurrentUserId(),id,body.version(),body.quotes(),body.allowModelProcessing()));}
 @PostMapping("/plans/{id}/questions") public Result<?> revise(@PathVariable long id,@RequestBody CareerWorkflowService.ReviseRequest body){return Result.success(workflow.revise(SecurityUtils.getCurrentUserId(),id,body));}
 @PostMapping("/plans/{id}/questions/{questionId}/regenerate") public Result<?> regenerate(@PathVariable long id,@PathVariable String questionId,@RequestBody(required=false) Regenerate body){return Result.success(workflow.regenerateQuestion(SecurityUtils.getCurrentUserId(),id,questionId,body==null?null:body.seed()));}
 @PostMapping("/records/{kind}/upload") public Result<?> upload(@PathVariable String kind,@RequestParam("file") org.springframework.web.multipart.MultipartFile file){return Result.success(uploads.upload(SecurityUtils.getCurrentUserId(),kind,file));}
 @GetMapping("/records/{id}/file") public ResponseEntity<byte[]> file(@PathVariable long id){var file=uploads.download(SecurityUtils.getCurrentUserId(),id);return ResponseEntity.ok().header("Content-Disposition","attachment; filename=private-document").header("Cache-Control","no-store").contentType(org.springframework.http.MediaType.parseMediaType(file.contentType())).body(file.bytes());}
 public record Version(int version){}
 public record Start(String requestId){}
 public record Parse(int version,boolean allowModelProcessing){}
 @PostMapping("/records/{id}/parse") public Result<?> parse(@PathVariable long id,@RequestBody Parse body){if(body==null)throw new BusinessException(400,"缺少解析参数");return Result.success(parsing.parse(SecurityUtils.getCurrentUserId(),id,body.version(),body.allowModelProcessing()));}
 @GetMapping("/records") public Result<?> list(@RequestParam String kind){return Result.success(records.list(SecurityUtils.getCurrentUserId(),kind));}
 @PostMapping("/records/{kind}") public Result<?> create(@PathVariable String kind,@RequestBody CareerRecordService.Edit body){return Result.success(records.create(SecurityUtils.getCurrentUserId(),kind,body));}
 @GetMapping("/records/{id}") public Result<?> detail(@PathVariable long id,@RequestParam(required=false) Integer version){return Result.success(records.detail(SecurityUtils.getCurrentUserId(),id,version));}
 @PutMapping("/records/{id}") public Result<?> update(@PathVariable long id,@RequestBody CareerRecordService.Edit body){return Result.success(records.update(SecurityUtils.getCurrentUserId(),id,body));}
 @GetMapping("/records/{id}/versions") public Result<?> versions(@PathVariable long id){return Result.success(records.versions(SecurityUtils.getCurrentUserId(),id));}
 @PostMapping("/records/{id}/confirm") public Result<?> confirm(@PathVariable long id,@RequestBody Version body){if(body==null)throw new BusinessException(400,"缺少版本");return Result.success(records.confirm(SecurityUtils.getCurrentUserId(),id,body.version()));}
 @PostMapping("/records/{id}/default") public Result<?> setDefault(@PathVariable long id){return Result.success(records.setDefault(SecurityUtils.getCurrentUserId(),id));}
 @DeleteMapping("/records/{id}") public Result<?> remove(@PathVariable long id){records.remove(SecurityUtils.getCurrentUserId(),id);return Result.success();}
 @GetMapping("/matches") public Result<?> matchHistory(){return Result.success(workspace.matchHistory(SecurityUtils.getCurrentUserId()));}
 @PostMapping("/toolbox/ats-check") public Result<?> atsCheck(@RequestBody AtsCheck body){if(body==null)throw new BusinessException(400,"缺少检查参数");return Result.success(toolbox.atsCheck(SecurityUtils.getCurrentUserId(),body.resumeId(),body.version(),body.jobId(),body.jobVersion()));}
 @PostMapping("/toolbox/content-check") public Result<?> contentCheck(@RequestBody ContentCheck body){if(body==null)throw new BusinessException(400,"缺少检查参数");return Result.success(toolbox.contentCheck(SecurityUtils.getCurrentUserId(),body.resumeId(),body.version()));}
 @PostMapping("/toolbox/jd-optimize") public Result<?> jdOptimize(@RequestBody JdOptimize body){if(body==null)throw new BusinessException(400,"缺少分析参数");return Result.success(toolbox.jdOptimize(SecurityUtils.getCurrentUserId(),body.resumeId(),body.resumeVersion(),body.jobId(),body.jobVersion()));}
 @GetMapping("/toolbox/history") public Result<?> toolHistory(@RequestParam(required=false) String toolType){return Result.success(toolbox.history(SecurityUtils.getCurrentUserId(),toolType));}
 @GetMapping("/toolbox/history/{id}") public Result<?> toolHistoryDetail(@PathVariable long id){return Result.success(toolbox.historyDetail(SecurityUtils.getCurrentUserId(),id));}
 @GetMapping("/records/{id}/compare") public Result<?> compare(@PathVariable long id,@RequestParam int from,@RequestParam int to){return Result.success(toolbox.compare(SecurityUtils.getCurrentUserId(),id,from,to));}
 @GetMapping("/templates") public Result<?> templates(){return Result.success(designs.templates());}
 @GetMapping("/records/{id}/designs") public Result<?> designs(@PathVariable long id){return Result.success(designs.list(SecurityUtils.getCurrentUserId(),id));}
 @PostMapping("/records/{id}/designs") public Result<?> createDesign(@PathVariable long id,@RequestBody CareerDesignService.DesignRequest body){return Result.success(designs.create(SecurityUtils.getCurrentUserId(),id,body));}
 @PutMapping("/records/{id}/designs/{designId}") public Result<?> updateDesign(@PathVariable long id,@PathVariable long designId,@RequestBody CareerDesignService.DesignRequest body){return Result.success(designs.update(SecurityUtils.getCurrentUserId(),id,designId,body));}
 @PostMapping("/records/{id}/designs/{designId}/default") public Result<?> defaultDesign(@PathVariable long id,@PathVariable long designId){designs.setDefault(SecurityUtils.getCurrentUserId(),id,designId);return Result.success();}
 @DeleteMapping("/records/{id}/designs/{designId}") public Result<?> removeDesign(@PathVariable long id,@PathVariable long designId){designs.remove(SecurityUtils.getCurrentUserId(),id,designId);return Result.success();}
 @GetMapping("/plans") public Result<?> planHistory(){return Result.success(workspace.planHistory(SecurityUtils.getCurrentUserId()));}
 @GetMapping("/interviews") public Result<?> interviewHistory(){return Result.success(workspace.interviewHistory(SecurityUtils.getCurrentUserId()));}
 @PostMapping("/matches/{id}/corrections") public Result<?> correct(@PathVariable long id,@RequestBody CareerWorkflowService.CorrectRequest body){return Result.success(workflow.correct(SecurityUtils.getCurrentUserId(),id,body));}
 @PostMapping("/matches") public Result<?> match(@RequestBody CareerWorkflowService.MatchRequest body){return Result.success(workflow.match(SecurityUtils.getCurrentUserId(),body));}
 @GetMapping("/matches/{id}") public Result<?> match(@PathVariable long id){return Result.success(workflow.get(SecurityUtils.getCurrentUserId(),id,"MATCH"));}
 @PostMapping("/plans") public Result<?> plan(@RequestBody CareerWorkflowService.PlanRequest body){return Result.success(workflow.plan(SecurityUtils.getCurrentUserId(),body));}
 @GetMapping("/plans/{id}") public Result<?> plan(@PathVariable long id){return Result.success(workflow.get(SecurityUtils.getCurrentUserId(),id,"PLAN"));}
 @PostMapping("/plans/{id}/start") public Result<?> start(@PathVariable long id,@RequestBody Start body){if(body==null)throw new BusinessException(400,"缺少创建标识");return Result.success(workflow.start(SecurityUtils.getCurrentUserId(),id,body.requestId()));}
 @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<?>> error(BusinessException error){int code=error.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,error.getMessage()));}
}
