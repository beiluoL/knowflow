package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
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
import java.util.stream.Collectors;

/**
 * 用户搜索接口（发起单聊时选择对话对象）
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserSearchController {

    private final SysUserMapper sysUserMapper;

    @Operation(summary = "按用户名/昵称/描述搜索用户（不含密码）")
    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam String keyword, Authentication authentication) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(SysUser::getUsername, keyword)
                .or()
                .like(SysUser::getNickname, keyword);
        wrapper.last("LIMIT 20");
        List<SysUser> users = sysUserMapper.selectList(wrapper);

        List<UserVO> vos = users.stream().map(u -> {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setEmail(u.getEmail());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setRole(u.getRole());
            return vo;
        }).collect(Collectors.toList());

        return Result.success(vos);
    }
}
