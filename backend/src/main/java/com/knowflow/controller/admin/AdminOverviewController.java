package com.knowflow.controller.admin;

import com.knowflow.common.Result;
import com.knowflow.service.AdminOverviewService;
import com.knowflow.vo.AdminOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员概览 REST 接口，提供后台看板统计数据。 */
@Tag(name = "管理员概览")
@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    @Operation(summary = "概览统计")
    @GetMapping
    public Result<AdminOverviewVO> overview() {
        return Result.success(adminOverviewService.getOverview());
    }
}
