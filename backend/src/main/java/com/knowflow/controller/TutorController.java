package com.knowflow.controller;
import com.knowflow.common.*;
import com.knowflow.learning.tutor.TutorContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/learning/tutor/sessions") @RequiredArgsConstructor
@ConditionalOnProperty(name="knowflow.product.tutor.enabled",havingValue="true")
public class TutorController {
 private final TutorContextService tutors;
 private final com.knowflow.learning.tutor.TutorAssessmentService assessments;
 public record Create(@NotNull @Positive Long noteId,@NotBlank String mode){}
 public record Append(@NotNull @Positive Long expectedRevision,boolean confirmed){}
 @PostMapping("/{id}/answers/{messageId}/to-note") public Result<com.knowflow.entity.LearningNote> append(@PathVariable long id,@PathVariable long messageId,@Valid @RequestBody Append input){return Result.success(tutors.appendAnswer(id,messageId,SecurityUtils.getCurrentUserId(),input.expectedRevision(),input.confirmed()));}
 public record Mode(@NotBlank String mode,@NotNull @Positive Long expectedRevision){}
 @GetMapping public Result<List<Map<String,Object>>> list(@RequestParam long noteId){return Result.success(tutors.list(noteId,SecurityUtils.getCurrentUserId()));}
 @PostMapping public Result<Map<String,Object>> create(@Valid @RequestBody Create input){return Result.success(tutors.create(input.noteId(),SecurityUtils.getCurrentUserId(),input.mode()));}
 @GetMapping("/{id}") public Result<Map<String,Object>> get(@PathVariable long id){return Result.success(tutors.get(id,SecurityUtils.getCurrentUserId()));}
 @GetMapping("/{id}/assessments") public Result<List<Map<String,Object>>> assessments(@PathVariable long id){return Result.success(assessments.list(id,SecurityUtils.getCurrentUserId()));}
 @PostMapping("/{id}/assessments") public Result<Map<String,Object>> assess(@PathVariable long id,@Valid @RequestBody com.knowflow.learning.tutor.TutorAssessmentService.Input input){return Result.success(assessments.assess(id,SecurityUtils.getCurrentUserId(),input));}
 @GetMapping("/{id}/hints") public Result<Map<String,Object>> hints(@PathVariable long id){return Result.success(tutors.hints(id,SecurityUtils.getCurrentUserId()));}
 @PostMapping("/{id}/mode") public Result<Map<String,Object>> mode(@PathVariable long id,@Valid @RequestBody Mode input){return Result.success(tutors.mode(id,SecurityUtils.getCurrentUserId(),input.mode(),input.expectedRevision()));}
}
