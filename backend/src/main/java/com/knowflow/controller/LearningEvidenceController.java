package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.learning.evidence.LearningEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/learning/evidence") @RequiredArgsConstructor
public class LearningEvidenceController {
    private final LearningEvidenceService evidence;
    @GetMapping public Result<Map<String,Object>> page(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String sourceType,@RequestParam(required=false) Long knowledgeId){return Result.success(evidence.page(SecurityUtils.getCurrentUserId(),page,size,sourceType,knowledgeId));}
    @GetMapping("/{sourceType}/{sourceId}") public Result<Map<String,Object>> get(@PathVariable String sourceType,@PathVariable long sourceId){return Result.success(evidence.get(SecurityUtils.getCurrentUserId(),sourceType,sourceId));}
    @PostMapping("/{sourceType}/{sourceId}/capture") public Result<Map<String,Object>> capture(@PathVariable String sourceType,@PathVariable long sourceId){return Result.success(evidence.capture(SecurityUtils.getCurrentUserId(),sourceType,sourceId));}
    @PostMapping("/retests") public Result<Map<String,Object>> link(@RequestBody LearningEvidenceService.Retest input){return Result.success(evidence.link(SecurityUtils.getCurrentUserId(),input));}
    @GetMapping("/comparisons") public Result<Map<String,Object>> compare(@RequestParam String baselineType,@RequestParam long baselineId,@RequestParam String retestType,@RequestParam long retestId){return Result.success(evidence.compare(SecurityUtils.getCurrentUserId(),baselineType,baselineId,retestType,retestId));}
}
