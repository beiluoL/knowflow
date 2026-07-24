package com.knowflow.controller.admin;

import com.knowflow.common.Result;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.vo.AdminOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员概览")
@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final SysUserMapper userMapper;
    private final DocDocumentMapper docMapper;
    private final DocCategoryMapper categoryMapper;
    private final ChatConversationMapper conversationMapper;
    private final LearningPathMapper pathMapper;

    @Operation(summary = "概览统计")
    @GetMapping
    public Result<AdminOverviewVO> overview() {
        AdminOverviewVO vo = new AdminOverviewVO();
        vo.setTotalUsers(userMapper.selectCount(null));
        vo.setTotalDocs(docMapper.selectCount(null));
        vo.setTotalCategories(categoryMapper.selectCount(null));
        vo.setTotalConversations(conversationMapper.selectCount(null));
        vo.setTotalLearningPaths(pathMapper.selectCount(null));
        vo.setTodayActiveUsers(0L);
        vo.setTodayNewUsers(0L);
        vo.setTodayNewDocs(0L);
        return Result.success(vo);
    }
}
