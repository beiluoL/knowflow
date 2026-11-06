package com.knowflow.learning.controller;

import com.knowflow.common.Result;
import com.knowflow.learning.service.UserLearningPrefService;
import com.knowflow.learning.dto.UserLearningPrefDTO;
import com.knowflow.learning.vo.UserLearningPrefVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 用户学习偏好接口。
 * 一个用户仅一条有效配置。
 * GET 返回当前偏好（不存在则返回默认值），POST 保存或更新。
 */
@Tag(name = "用户学习偏好接口")
@RestController
@RequestMapping("/api/learning/prefs")
@RequiredArgsConstructor
public class UserLearningPrefController {

    private final UserLearningPrefService preferenceService;

    /** 获取当前用户偏好；未保存时返回默认值。 */
    @Operation(summary = "获取学习偏好")
    @GetMapping
    public Result<UserLearningPrefVO> get(Authentication authentication) {
        return Result.success(preferenceService.get());
    }

    /** 仅合并请求中的非空偏好字段。 */
    @Operation(summary = "保存学习偏好")
    @PostMapping
    public Result<UserLearningPrefVO> save(@RequestBody UserLearningPrefDTO dto, Authentication authentication) {
        return Result.success(preferenceService.save(dto));
    }
}
