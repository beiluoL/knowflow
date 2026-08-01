package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.LearningNoteDTO;
import com.knowflow.entity.LearningNote;
import com.knowflow.mapper.LearningNoteMapper;
import com.knowflow.vo.LearningNoteVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 章节学习笔记接口。
 * 提供按章节/路径维度的笔记 CRUD，笔记自动关联当前登录用户。
 */
@Tag(name = "章节学习笔记接口")
@RestController
@RequestMapping("/api/learning/notes")
@RequiredArgsConstructor
public class LearningNoteController {

    private final LearningNoteMapper noteMapper;

    /** 查询当前用户的笔记列表，可按 chapterId / pathId 过滤 */
    @Operation(summary = "查询笔记列表（可按章节/路径过滤）")
    @GetMapping
    public Result<List<LearningNoteVO>> list(
            @RequestParam(required = false) Long chapterId,
            @RequestParam(required = false) Long pathId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LambdaQueryWrapper<LearningNote> wrapper = new LambdaQueryWrapper<LearningNote>()
                .eq(LearningNote::getUserId, userId)
                .orderByDesc(LearningNote::getUpdateTime);
        if (chapterId != null) {
            wrapper.eq(LearningNote::getChapterId, chapterId);
        }
        if (pathId != null) {
            wrapper.eq(LearningNote::getPathId, pathId);
        }
        List<LearningNote> notes = noteMapper.selectList(wrapper);
        List<LearningNoteVO> voList = notes.stream().map(this::toVO).collect(Collectors.toList());
        return Result.success(voList);
    }

    /** 获取笔记详情 */
    @Operation(summary = "获取笔记详情")
    @GetMapping("/{id}")
    public Result<LearningNoteVO> detail(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LearningNote note = noteMapper.selectById(id);
        if (note == null || !userId.equals(note.getUserId())) {
            return Result.success(null);
        }
        return Result.success(toVO(note));
    }

    /** 新增笔记（自动绑定当前用户，可携带 chapterId/pathId 上下文） */
    @Operation(summary = "新增笔记")
    @PostMapping
    public Result<LearningNoteVO> create(@Valid @RequestBody LearningNoteDTO dto, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LearningNote note = new LearningNote();
        note.setUserId(userId);
        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        note.setChapterId(dto.getChapterId());
        note.setPathId(dto.getPathId());
        noteMapper.insert(note);
        return Result.success(toVO(note));
    }

    /** 编辑笔记（仅本人） */
    @Operation(summary = "编辑笔记")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LearningNoteDTO dto,
                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LearningNote note = noteMapper.selectById(id);
        if (note == null || !userId.equals(note.getUserId())) {
            return Result.error(404, "笔记不存在");
        }
        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        if (dto.getChapterId() != null) note.setChapterId(dto.getChapterId());
        if (dto.getPathId() != null) note.setPathId(dto.getPathId());
        noteMapper.updateById(note);
        return Result.success();
    }

    /** 删除笔记（仅本人，逻辑删除） */
    @Operation(summary = "删除笔记")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LearningNote note = noteMapper.selectById(id);
        if (note == null || !userId.equals(note.getUserId())) {
            return Result.error(404, "笔记不存在");
        }
        noteMapper.deleteById(id);
        return Result.success();
    }

    private LearningNoteVO toVO(LearningNote note) {
        LearningNoteVO vo = new LearningNoteVO();
        vo.setId(note.getId());
        vo.setUserId(note.getUserId());
        vo.setChapterId(note.getChapterId());
        vo.setPathId(note.getPathId());
        vo.setTitle(note.getTitle());
        vo.setContent(note.getContent());
        vo.setCreateTime(note.getCreateTime());
        vo.setUpdateTime(note.getUpdateTime());
        return vo;
    }
}
