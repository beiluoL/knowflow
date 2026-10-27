package com.knowflow.controller;
import com.knowflow.common.*;
import com.knowflow.dto.LearningNoteDTO;
import com.knowflow.entity.*;
import com.knowflow.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** 当前用户私人笔记 API；管理员也不能越过所有者约束。 */
@RestController @RequestMapping("/api/notes") @RequiredArgsConstructor
public class NoteController {
 private final NoteService notes;
 private final com.knowflow.service.NoteOrganizationService organization;
 private Long uid(){return SecurityUtils.getCurrentUserId();}
 public record Revision(Long revision) {}
 @GetMapping public Result<PageResult<LearningNote>> list(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="30") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="false") boolean trash,@RequestParam(defaultValue="ALL") String view,@RequestParam(required=false) Long folderId,@RequestParam(required=false) List<Long> tagIds,@RequestParam(defaultValue="UPDATED") String sort){return Result.success(organization.page(uid(),pageNum,pageSize,keyword,trash?"TRASH":view,folderId,tagIds,sort));}
 public record FolderInput(String name,Long parentId,Long revision){}
 public record TagInput(String name){}
 public record RelationInput(String type,Long targetId){}
 @GetMapping("/stats") public Result<?> stats(){return Result.success(organization.stats(uid()));}
 @GetMapping("/folders") public Result<?> folders(){return Result.success(organization.folders(uid()));}
 @PostMapping("/folders") public Result<?> folder(@RequestBody FolderInput d){return Result.success(organization.folder(uid(),null,d.name(),d.parentId(),null));}
 @PutMapping("/folders/{id}") public Result<?> folderUpdate(@PathVariable Long id,@RequestBody FolderInput d){return Result.success(organization.folder(uid(),id,d.name(),d.parentId(),d.revision()));}
 @DeleteMapping("/folders/{id}") public Result<?> folderDelete(@PathVariable Long id,@RequestParam Long revision){organization.deleteFolder(uid(),id,revision);return Result.success();}
 @GetMapping("/tags") public Result<?> tags(){return Result.success(organization.tags(uid()));}
 @PostMapping("/tags") public Result<?> tag(@RequestBody TagInput d){return Result.success(organization.createTag(uid(),d.name()));}
 @PutMapping("/tags/{id}") public Result<?> tagRename(@PathVariable Long id,@RequestBody TagInput d){organization.editTag(uid(),id,d.name(),false);return Result.success();}
 @DeleteMapping("/tags/{id}") public Result<?> tagDelete(@PathVariable Long id){organization.editTag(uid(),id,null,true);return Result.success();}
 @GetMapping("/{id}/relations") public Result<?> relations(@PathVariable Long id){return Result.success(organization.relations(uid(),id));}
 @PostMapping("/{id}/relations") public Result<?> relation(@PathVariable Long id,@RequestBody RelationInput d){return Result.success(organization.addRelation(uid(),id,d.type(),d.targetId()));}
 @DeleteMapping("/{id}/relations/{relation}") public Result<?> relationDelete(@PathVariable Long id,@PathVariable Long relation){organization.removeRelation(uid(),id,relation);return Result.success();}

 @GetMapping("/{id}") public Result<LearningNote> detail(@PathVariable Long id){return Result.success(notes.detail(id,uid(),false));}
 @PostMapping public Result<LearningNote> create(@Valid @RequestBody LearningNoteDTO dto){return Result.success(notes.create(uid(),dto));}
 @PutMapping("/{id}") public Result<LearningNote> update(@PathVariable Long id,@Valid @RequestBody LearningNoteDTO dto){return Result.success(notes.update(id,uid(),dto));}
 @DeleteMapping("/{id}") public Result<LearningNote> delete(@PathVariable Long id,@RequestParam Long revision){return Result.success(notes.delete(id,uid(),revision));}
 @PostMapping("/{id}/restore") public Result<LearningNote> restore(@PathVariable Long id,@RequestBody Revision dto){return Result.success(notes.restore(id,uid(),dto.revision()));}
 @GetMapping("/{id}/versions") public Result<List<NoteVersion>> versions(@PathVariable Long id){return Result.success(notes.history(id,uid()));}
 @PostMapping("/{id}/versions/{version}/restore") public Result<LearningNote> restoreVersion(@PathVariable Long id,@PathVariable Long version,@RequestBody Revision dto){return Result.success(notes.restoreVersion(id,uid(),version,dto.revision()));}
 @PostMapping("/import") public Result<LearningNote> importLegacy(@Valid @RequestBody LearningNoteDTO dto){try{return Result.success(notes.importLegacy(uid(),dto));}catch(org.springframework.dao.DuplicateKeyException e){throw new com.knowflow.exception.BusinessException(409,"此条迁移正在其他位置提交，请重试核对");}}
}
