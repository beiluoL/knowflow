package com.knowflow.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.mapper.SysUserMapper;
import com.knowflow.identity.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** 单聊收件人搜索，只投影公开资料，不读取邮箱或认证字段。 */
@Service
@RequiredArgsConstructor
public class UserSearchService {
    private final SysUserMapper userMapper;

    /** 返回至多20名匹配用户名或昵称的用户；不暴露邮箱及认证信息。 */
    public List<UserVO> search(String keyword) {
        var query = new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getNickname, SysUser::getAvatar, SysUser::getRole)
                .and(match -> match.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword))
                .last("LIMIT 20");
        return userMapper.selectList(query).stream().map(user -> {
            UserVO result = new UserVO();
            result.setId(user.getId());
            result.setUsername(user.getUsername());
            result.setNickname(user.getNickname());
            result.setAvatar(user.getAvatar());
            result.setRole(user.getRole());
            return result;
        }).toList();
    }
}
