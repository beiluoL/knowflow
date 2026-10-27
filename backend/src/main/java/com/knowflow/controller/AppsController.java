package com.knowflow.controller;

import com.knowflow.apps.UserAppService;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.UserAppUpdateRequest;
import com.knowflow.vo.UserAppVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户应用状态 REST 接口（Architecture-3，§29-33）。
 * 最小 API：GET 查询 + PUT 更新（幂等 upsert）；userId 一律来自 SecurityContext（§33/§256-260）。
 * 不新增 /apps/all、/apps/catalog、/apps/install、/apps/enable、/apps/disable 等重复接口（§30）。
 */
@Tag(name = "应用平台接口")
@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class AppsController {

    private final UserAppService userAppService;

    @Operation(summary = "我的应用状态（含默认初始化）")
    @GetMapping("/user-apps")
    public Result<List<UserAppVO>> userApps() {
        return Result.success(userAppService.getUserApps(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "更新我的应用状态（幂等 upsert）")
    @PutMapping("/user-apps/{appKey}")
    public Result<UserAppVO> updateUserApp(@PathVariable String appKey,
                                           @Valid @RequestBody UserAppUpdateRequest req) {
        return Result.success(userAppService.upsert(
                SecurityUtils.getCurrentUserId(), appKey,
                req.getInstalled(), req.getEnabled(), req.getSortOrder()));
    }
}
