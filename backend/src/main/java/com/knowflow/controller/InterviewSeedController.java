package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.seed.InterviewSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/learning/seed/java-ai-v1")
public class InterviewSeedController {
    private final InterviewSeedService seed;
    private final com.knowflow.seed.CollectionsDepthService depth;
    private final com.knowflow.seed.GenericsDepthService generics;
    private final com.knowflow.seed.ContentRebuildService rebuild;
    private final org.springframework.beans.factory.ObjectProvider<com.knowflow.seed.ContentStudioHashMapService> studioQa;
    private final org.springframework.beans.factory.ObjectProvider<com.knowflow.seed.ContentStudioThreadPoolService> threadPoolQa;
    @GetMapping("/content-rebuild") public Result<Map<String,Object>> rebuildDryRun(){return Result.success(rebuild.dryRun(SecurityUtils.getCurrentUserId()));}
    @PostMapping("/content-rebuild") public Result<Map<String,Object>> rebuildImport(){return Result.success(rebuild.activate(SecurityUtils.getCurrentUserId()));}
    @PostMapping("/generics-depth") public Result<Map<String,Object>> upgradeGenerics() {return Result.success(generics.activate(SecurityUtils.getCurrentUserId()));}
    @PostMapping("/collections-depth") public Result<Map<String,Object>> upgradeCollections() {return Result.success(depth.activate(SecurityUtils.getCurrentUserId()));}
    @GetMapping public Result<Map<String,Object>> status() {return Result.success(seed.status(SecurityUtils.getCurrentUserId()));}
    @PostMapping public Result<Map<String,Object>> activate() {return Result.success(seed.activate(SecurityUtils.getCurrentUserId()));}
    @GetMapping("/chapters/{id}") public Result<Map<String,Object>> chapterResources(@PathVariable Long id) {
        Long actor=SecurityUtils.getCurrentUserId();var result=seed.chapterResources(actor,id);
        if(result!=null){
            boolean isGenerics=generics.available(id);
            result.put("upgradeAvailable",isGenerics||depth.available(id));
            result.put("upgradeKind",isGenerics?"generics":"hashmap");
            result.put("readingContent",generics.preview(id));
            var previous=isGenerics?generics.revision(actor,id):depth.revision(actor,id);
            result.put("revision",previous);
            result.put("readingUpgradeAvailable",isGenerics&&generics.readingUpgradeAvailable(actor,id));
            result.put("rebuildAvailable",rebuild.available(id));
            var revision=rebuild.revision(actor,id);
            if(revision!=null){
                var selected=new java.util.LinkedHashMap<String,Object>(revision);
                if(previous!=null)selected.put("previousReadingDocId",previous.get("docId"));
                result.put("revision",selected);
                result.put("readingUpgradeAvailable",false);
            }
        }
        var pilot=studioQa.getIfAvailable();
        if(result!=null && pilot!=null){
            var revision=pilot.revision(actor,id);
            if(revision!=null){result.put("revision",revision);result.put("rebuildAvailable",false);result.put("readingUpgradeAvailable",false);}
        }
        var threadPool=threadPoolQa.getIfAvailable();
        if(result!=null && threadPool!=null){var revision=threadPool.revision(actor,id);if(revision!=null){result.put("revision",revision);result.put("rebuildAvailable",false);result.put("readingUpgradeAvailable",false);}}
        return Result.success(result);
    }
}
