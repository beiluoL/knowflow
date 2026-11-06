package com.knowflow.interview.controller.career;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.knowflow.interview.service.career.ResumeDraftService;
/** 草稿API始终由认证上下文确定用户，不接受客户端身份。 */
@RestController @RequestMapping("/api/interview/career/studio/resumes/{id}/draft") @RequiredArgsConstructor
public class ResumeDraftController {
 private final ResumeDraftService drafts;
 @GetMapping public Result<?> get(@PathVariable long id){return Result.success(drafts.get(SecurityUtils.getCurrentUserId(),id));}
 @PutMapping public Result<?> save(@PathVariable long id,@RequestBody ResumeDraftService.Draft body){return Result.success(drafts.save(SecurityUtils.getCurrentUserId(),id,body));}
 @DeleteMapping public Result<?> remove(@PathVariable long id,@RequestParam long revision){return Result.success(drafts.remove(SecurityUtils.getCurrentUserId(),id,revision));}
}
