package com.zhishiku.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhishiku.dto.LoginDTO;
import com.zhishiku.dto.RegisterDTO;
import com.zhishiku.entity.SysUser;
import com.zhishiku.vo.LoginVO;
import com.zhishiku.vo.UserStatsVO;
import com.zhishiku.vo.UserVO;

public interface UserService extends IService<SysUser> {

    LoginVO login(LoginDTO dto);

    LoginVO register(RegisterDTO dto);

    UserVO getCurrentUser(Long userId);

    UserStatsVO getUserStats(Long userId);
}
