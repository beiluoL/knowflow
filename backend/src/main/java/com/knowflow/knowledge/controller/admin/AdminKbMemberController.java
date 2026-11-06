package com.knowflow.knowledge.controller.admin;

import com.knowflow.common.Result;
import com.knowflow.knowledge.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库成员与权限管理 REST 接口。
 * 所有接口都需登录（Spring Security 已默认拦截 /api/**）；
 * 对于「写操作」（新增成员、修改角色、移除成员），要求操作人对该知识库是 OWNER 或系统 ADMIN。
 */
@Tag(name = "管理员-知识库成员与权限")
@RestController
@RequestMapping("/api/admin/kb-members")
@RequiredArgsConstructor
public class AdminKbMemberController {

    private final com.knowflow.knowledge.service.AdminKbMemberApplicationService applicationService;

    public static class AddMemberReq extends com.knowflow.knowledge.service.AdminKbMemberApplicationService.AddMemberReq {}
    public static class ChangeRoleReq extends com.knowflow.knowledge.service.AdminKbMemberApplicationService.ChangeRoleReq {}

    // ========== 查询 ==========

    @Operation(summary = "知识库成员列表（含用户信息聚合）")
    @GetMapping("/category/{categoryId}")
    public Result<List<com.knowflow.knowledge.service.AdminKbMemberApplicationService.MemberVO>> listByCategory(@PathVariable Long categoryId) {
        return applicationService.listByCategory(categoryId);
    }

    @Operation(summary = "按关键字搜索可邀请用户（用户名/昵称/邮箱，排除已加入者）")
    @GetMapping("/search-users")
    public Result<List<com.knowflow.knowledge.service.AdminKbMemberApplicationService.MemberVO>> searchUsers(
            @RequestParam Long categoryId,
            @RequestParam String keyword) {
        return applicationService.searchUsers(categoryId, keyword);
    }

    // ========== 新增/邀请 ==========

    @Operation(summary = "添加成员 / 发送邀请（支持已注册用户或邮箱邀请未注册用户）")
    @PostMapping
    public Result<com.knowflow.knowledge.service.AdminKbMemberApplicationService.MemberVO> add(@RequestBody AddMemberReq req) {
        return applicationService.add(req);
    }

    // ========== 修改/删除 ==========

    @Operation(summary = "变更成员角色")
    @PutMapping("/{memberId}/role")
    public Result<Void> changeRole(@PathVariable Long memberId, @RequestBody ChangeRoleReq req) {
        return applicationService.changeRole(memberId, req);
    }

    @Operation(summary = "移除成员")
    @DeleteMapping("/{memberId}")
    public Result<Void> remove(@PathVariable Long memberId) {
        return applicationService.remove(memberId);
    }

    // ========== 辅助 ==========

    /**
     * 内部工具：创建知识库时自动把创建者设为 OWNER。
     * 保留旧 Java 调用入口；业务操作由成员应用服务负责。
     */
    public void ensureOwnerForCategory(Long categoryId, Long ownerId) {
        applicationService.ensureOwnerForCategory(categoryId, ownerId);
    }
}
