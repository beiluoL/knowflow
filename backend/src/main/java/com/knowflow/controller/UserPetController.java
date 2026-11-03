package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.FocusGainDTO;
import com.knowflow.dto.UserPetUpdateDTO;
import com.knowflow.vo.UserPetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 用户学习宠物接口。
 * 提供宠物获取/喂食/玩耍/专注获经验/更新外观等互动能力，互动会累积经验并触发升级。
 */
@Tag(name = "用户学习宠物接口")
@RestController
@RequestMapping("/api/learning/pet")
@RequiredArgsConstructor
public class UserPetController {

    private final com.knowflow.service.UserPetService petService;

    @GetMapping
    public Result<UserPetVO> get(Authentication authentication) {
        return Result.success(petService.get(SecurityUtils.getCurrentUserId()));
    }
    @PostMapping("/feed")
    public Result<UserPetVO> feed(Authentication authentication) {
        return Result.success(petService.feed(SecurityUtils.getCurrentUserId()));
    }
    @PostMapping("/play")
    public Result<UserPetVO> play(Authentication authentication) {
        return Result.success(petService.play(SecurityUtils.getCurrentUserId()));
    }
    @PostMapping("/focus-gain")
    public Result<UserPetVO> focusGain(@Valid @RequestBody FocusGainDTO dto, Authentication authentication) {
        return Result.success(petService.focusGain(dto, SecurityUtils.getCurrentUserId()));
    }
    @PutMapping
    public Result<UserPetVO> update(@Valid @RequestBody UserPetUpdateDTO dto, Authentication authentication) {
        return Result.success(petService.update(dto, SecurityUtils.getCurrentUserId()));
    }
}
