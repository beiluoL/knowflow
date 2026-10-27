package com.knowflow.interview.speech;
import com.knowflow.common.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/interview")
public class InterviewSpeechController {
 private final InterviewSpeechService service;
 public InterviewSpeechController(InterviewSpeechService service){this.service=service;}
 @GetMapping("/speech/capability") public Result<?> capability(){SecurityUtils.getCurrentUserId();return Result.success(service.capability());}
 @PostMapping(value="/sessions/{id}/speech",consumes="multipart/form-data")
 public Result<?> transcribe(@PathVariable Long id,@RequestParam String questionId,@RequestParam String requestId,@RequestParam long durationMs,@RequestPart MultipartFile file){return Result.success(service.transcribe(SecurityUtils.getCurrentUserId(),id,questionId,requestId,durationMs,file));}
}
