package com.knowflow.interview.career;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

/** 简历、岗位和匹配仅接受服务端身份，资源归属由Service再次校验。 */
@RestController @RequestMapping("/api/interview/career") @RequiredArgsConstructor
public class CareerController {
 private final CareerRecordService records;
 private final CareerWorkflowService workflow;
 private final CareerParsingService parsing;
 private final CareerUploadService uploads;
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
 @GetMapping("/matches") public Result<?> matchHistory(){return Result.success(workflow.history(SecurityUtils.getCurrentUserId(),"MATCH"));}
 @PostMapping("/matches/{id}/corrections") public Result<?> correct(@PathVariable long id,@RequestBody CareerWorkflowService.CorrectRequest body){return Result.success(workflow.correct(SecurityUtils.getCurrentUserId(),id,body));}
 @PostMapping("/matches") public Result<?> match(@RequestBody CareerWorkflowService.MatchRequest body){return Result.success(workflow.match(SecurityUtils.getCurrentUserId(),body));}
 @GetMapping("/matches/{id}") public Result<?> match(@PathVariable long id){return Result.success(workflow.get(SecurityUtils.getCurrentUserId(),id,"MATCH"));}
 @PostMapping("/plans") public Result<?> plan(@RequestBody CareerWorkflowService.PlanRequest body){return Result.success(workflow.plan(SecurityUtils.getCurrentUserId(),body));}
 @GetMapping("/plans/{id}") public Result<?> plan(@PathVariable long id){return Result.success(workflow.get(SecurityUtils.getCurrentUserId(),id,"PLAN"));}
 @PostMapping("/plans/{id}/start") public Result<?> start(@PathVariable long id,@RequestBody Start body){if(body==null)throw new BusinessException(400,"缺少创建标识");return Result.success(workflow.start(SecurityUtils.getCurrentUserId(),id,body.requestId()));}
 @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<?>> error(BusinessException error){int code=error.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,error.getMessage()));}
}
