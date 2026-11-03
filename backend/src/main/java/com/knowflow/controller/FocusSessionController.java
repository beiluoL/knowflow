package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.FocusSessionEndDTO;
import com.knowflow.entity.FocusSession;
import com.knowflow.vo.FocusStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "专注会话接口")
@RestController
@RequestMapping("/api/focus/sessions")
@RequiredArgsConstructor
@Slf4j
public class FocusSessionController {

    private final com.knowflow.service.FocusSessionService focusSessionService;

    @PostMapping("/start")
    public Result<FocusSession> start(@RequestParam(required = false) String mode) {
        return Result.success(focusSessionService.start(mode));
    }
    @PostMapping("/{id}/end")
    public Result<FocusSession> end(@PathVariable Long id, @Valid @RequestBody FocusSessionEndDTO dto) {
        return Result.success(focusSessionService.end(id, dto));
    }
    @GetMapping("/today")
    public Result<List<FocusSession>> today() { return Result.success(focusSessionService.today()); }
    @GetMapping("/stats")
    public Result<FocusStatsVO> stats(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(focusSessionService.stats(days));
    }
    @GetMapping("/{id}")
    public Result<FocusSession> getById(@PathVariable Long id) { return Result.success(focusSessionService.getById(id)); }
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { focusSessionService.delete(id); return Result.success(); }
}
