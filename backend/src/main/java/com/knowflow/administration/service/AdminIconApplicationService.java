package com.knowflow.administration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.administration.entity.SysIcon;
import com.knowflow.administration.mapper.SysIconMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import org.springframework.stereotype.Service;

/** 管理用例编排及持久化边界。 */
@Service
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminIconApplicationService {
    private final SysIconMapper iconMapper;
    /** 读取认证身份。 */
    private Long getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<List<SysIcon>> list() {
        QueryWrapper<SysIcon> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0).orderByDesc("create_time");
        return Result.success(iconMapper.selectList(wrapper));
    }

    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<SysIcon> create(SysIcon icon) {
        if (icon.getName() == null || icon.getName().isBlank()) {
            return Result.error(400, "图标名称不能为空");
        }
        if (icon.getContent() == null || icon.getContent().isBlank()) {
            return Result.error(400, "图标内容不能为空");
        }
        icon.setType("custom");
        icon.setUserId(getCurrentUserId());
        iconMapper.insert(icon);
        return Result.success(icon);
    }

    /** 执行管理操作，保留既有校验与响应合同。 */
    public Result<Void> delete(Long id) {
        SysIcon icon = iconMapper.selectById(id);
        if (icon == null || icon.getDeleted() != null && icon.getDeleted() == 1) {
            return Result.error(404, "图标不存在");
        }
        icon.setDeleted(1);
        iconMapper.updateById(icon);
        return Result.success(null);
    }
}
