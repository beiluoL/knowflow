package com.knowflow.controller;
import com.knowflow.common.*;
import com.knowflow.dto.LearningNoteDTO;
import com.knowflow.entity.LearningNote;
import com.knowflow.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** 保留章节笔记原 URL 与列表结构，正文统一经笔记服务处理。 */
@RestController @RequestMapping("/api/learning/notes") @RequiredArgsConstructor
public class LearningNoteController {
 private final NoteService notes;
 private Long uid(){return SecurityUtils.getCurrentUserId();}
 @GetMapping public Result<List<LearningNote>> list(@RequestParam(required=false) Long chapterId,@RequestParam(required=false) Long pathId){return Result.success(notes.chapterList(uid(),chapterId,pathId));}
 @GetMapping("/{id}") public Result<LearningNote> detail(@PathVariable Long id){return Result.success(notes.detail(id,uid(),false));}
 @PostMapping public Result<LearningNote> create(@Valid @RequestBody LearningNoteDTO dto){return Result.success(notes.create(uid(),dto));}
 @PutMapping("/{id}") public Result<LearningNote> update(@PathVariable Long id,@Valid @RequestBody LearningNoteDTO dto){return Result.success(notes.update(id,uid(),dto));}
 @DeleteMapping("/{id}") public Result<LearningNote> delete(@PathVariable Long id,@RequestParam Long revision){return Result.success(notes.delete(id,uid(),revision));}
}
