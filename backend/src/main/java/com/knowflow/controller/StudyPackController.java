package com.knowflow.controller;
import com.knowflow.common.*;
import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.learning.studypack.StudyPackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@RestController @RequestMapping("/api/learning/study-packs") @RequiredArgsConstructor
@ConditionalOnProperty(name={"knowflow.product.study-pack.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled"},havingValue="true")
public class StudyPackController {
 private final StudyPackService packs;
 private long user(){return SecurityUtils.getCurrentUserId();}
 public record Retry(@NotBlank String step){}
 public record Cards(@NotNull @Positive Long expectedRevision,@NotNull @Size(min=5,max=5) List<FlashcardSaveDTO> cards){}
 @GetMapping public Result<List<Map<String,Object>>> list(){return Result.success(packs.list(user()));}
 @PostMapping(consumes="multipart/form-data") public Result<Map<String,Object>> upload(@RequestPart("file") MultipartFile file){return Result.success(packs.upload(user(),file));}
 @GetMapping("/{id}") public Result<Map<String,Object>> detail(@PathVariable long id){return Result.success(packs.detail(id,user()));}
 @PostMapping("/{id}/retry") public Result<Map<String,Object>> retry(@PathVariable long id,@Valid @RequestBody Retry input){return Result.success(packs.retry(id,user(),input.step()));}
 @PutMapping("/{id}/draft") public Result<Map<String,Object>> draft(@PathVariable long id,@Valid @RequestBody Cards input){return Result.success(packs.saveCards(id,user(),input.expectedRevision(),input.cards()));}
 @PostMapping("/{id}/accept") public Result<Map<String,Object>> accept(@PathVariable long id,@Valid @RequestBody Cards input){return Result.success(packs.accept(id,user(),input.expectedRevision(),input.cards()));}
}
