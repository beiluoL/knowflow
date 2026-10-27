package com.knowflow.controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.seed.InterviewRadarImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/learning/interview-radar/import")
public class InterviewRadarImportController {
 private final InterviewRadarImportService service;
 @PostMapping("/preview") public Result<?> preview(@RequestBody JsonNode input){return Result.success(service.preview(SecurityUtils.getCurrentUserId(),input));}
 @PostMapping public Result<?> apply(@RequestBody JsonNode input){return Result.success(service.apply(SecurityUtils.getCurrentUserId(),input));}
}
