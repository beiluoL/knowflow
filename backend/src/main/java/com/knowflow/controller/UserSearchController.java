package com.knowflow.controller;

import com.knowflow.common.Result;

import com.knowflow.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户搜索接口（发起单聊时选择对话对象）
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserSearchController {

    private final com.knowflow.service.UserSearchService userSearchService;

    @Operation(summary = "按用户名/昵称/描述搜索用户（不含密码）")
    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam String keyword, Authentication authentication) {
        return Result.success(userSearchService.search(keyword));
    }
}
