package com.zhishiku.controller;

import com.zhishiku.common.Result;
import com.zhishiku.service.UserService;
import com.zhishiku.vo.UserStatsVO;
import com.zhishiku.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "个人资料")
    @GetMapping("/profile")
    public Result<UserVO> profile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(userService.getCurrentUser(userId));
    }

    @Operation(summary = "学习统计")
    @GetMapping("/stats")
    public Result<UserStatsVO> stats(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(userService.getUserStats(userId));
    }
}
