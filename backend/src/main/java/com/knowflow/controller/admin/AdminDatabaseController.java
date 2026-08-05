package com.knowflow.controller.admin;

import com.knowflow.common.Result;
import com.knowflow.dto.DbSwitchDTO;
import com.knowflow.service.DatabaseAdminService;
import com.knowflow.vo.DbStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据库设置管理接口。
 * <p>
 * 路径位于 /api/admin/** 之下，已由 SecurityConfig 限定仅 ADMIN 角色可访问，
 * 防止普通用户切换生产数据库。
 */
@Slf4j
@Tag(name = "数据库设置")
@RestController
@RequestMapping("/api/admin/database")
@RequiredArgsConstructor
public class AdminDatabaseController {

    private final DatabaseAdminService databaseAdminService;

    @Operation(summary = "查询当前数据库状态与可选类型")
    @GetMapping("/status")
    public Result<DbStatusVO> status() {
        return Result.success(databaseAdminService.status());
    }

    @Operation(summary = "测试目标数据库连通性")
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@Valid @RequestBody DbSwitchDTO dto) {
        return Result.success(databaseAdminService.testConnection(dto));
    }

    @Operation(summary = "热切换数据源")
    @PostMapping("/switch")
    public Result<DbStatusVO> switchDataSource(@Valid @RequestBody DbSwitchDTO dto) {
        try {
            DbStatusVO vo = databaseAdminService.switchDataSource(dto);
            return Result.success("数据库已切换为 " + vo.getDisplayName(), vo);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 切换失败属可预期的业务异常（连接不通/被禁用），返回明确原因而非 500 堆栈
            log.warn("数据库切换失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "对当前数据库执行初始化脚本")
    @PostMapping("/init")
    public Result<Map<String, Object>> init() {
        return Result.success(databaseAdminService.initSchema());
    }
}
