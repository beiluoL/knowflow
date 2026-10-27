package com.knowflow.catalog;
import com.knowflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/question-catalog")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class QuestionCatalogController {
 private final QuestionCatalogService service;
 private final com.knowflow.coding.judge.CodeJudgeGateway judge;
 @GetMapping("/judge-status") public Result<?> judgeStatus(){return Result.success(java.util.Map.of("mode",judge.mode(),"executionEnabled",judge.executionEnabled(),"productionSandboxReady",false));}
 @GetMapping public Result<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String questionType,@RequestParam(required=false) String sourceType,@RequestParam(required=false) Integer difficulty,@RequestParam(required=false) Integer status,@RequestParam(required=false) String reviewStatus,@RequestParam(required=false) Long categoryId,@RequestParam(required=false) String topicCode){return Result.success(service.page(page,pageSize,keyword,questionType,sourceType,difficulty,status,reviewStatus,categoryId,topicCode));}
 public record BatchStatus(java.util.List<String> catalogIds,boolean published){}
 @PostMapping("/batch-status") public Result<?> batch(@RequestBody BatchStatus body){service.batchStatus(body.catalogIds(),body.published());return Result.success();}
 @GetMapping("/audit") public Result<?> audit(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return Result.success(service.auditPage(page,pageSize));}
 @GetMapping("/facets") public Result<?> facets(){return Result.success(service.facets());}
 @PutMapping("/{catalogId}/basic") public Result<?> basic(@PathVariable String catalogId,@RequestBody QuestionCatalogService.BasicEdit body){service.editBasic(catalogId,body);return Result.success();}
 @GetMapping("/{catalogId}") public Result<?> detail(@PathVariable String catalogId){return Result.success(service.detail(catalogId));}
}
