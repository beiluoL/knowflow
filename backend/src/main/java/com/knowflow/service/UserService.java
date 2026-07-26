package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.LoginDTO;
import com.knowflow.dto.RegisterDTO;
import com.knowflow.entity.SysUser;
import com.knowflow.vo.LoginVO;
import com.knowflow.vo.UserStatsVO;
import com.knowflow.dto.UpdateProfileDTO;
import com.knowflow.vo.UserVO;

/** 用户业务服务接口。 */
public interface UserService extends IService<SysUser> {

    LoginVO login(LoginDTO dto);

    LoginVO register(RegisterDTO dto);

    UserVO getCurrentUser(Long userId);

    UserStatsVO getUserStats(Long userId);

    UserVO updateProfile(Long userId, UpdateProfileDTO dto);
}
