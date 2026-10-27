package com.knowflow.interview.radar;
import com.knowflow.common.*;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/api/interview/radar")
public class RadarStudyController {
 private final RadarStudyService study;
 @GetMapping("/questions") public Result<?> questions(){return Result.success(study.questions(SecurityUtils.getCurrentUserId()));}
 @GetMapping("/overview") public Result<?> overview(){return Result.success(study.overview(SecurityUtils.getCurrentUserId()));}
 @PostMapping("/sessions") public Result<?> start(@RequestBody RadarStudyService.Start body){return Result.success(study.start(SecurityUtils.getCurrentUserId(),body));}
 @PostMapping("/questions/{id}/review") public Result<?> review(@PathVariable long id){return Result.success(study.collectReview(SecurityUtils.getCurrentUserId(),id));}
 @PostMapping("/plans") public Result<?> plan(@RequestBody Map<String,Integer> body){if(body==null||body.get("minutes")==null)throw new BusinessException(400,"请选择学习时长");return Result.success(study.savePlan(SecurityUtils.getCurrentUserId(),body.get("minutes")));}
 @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<?>> business(BusinessException e){int code=e.getCode()==null?500:e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,e.getMessage()));}
}
