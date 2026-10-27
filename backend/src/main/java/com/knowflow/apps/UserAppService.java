package com.knowflow.apps;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.UserApp;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.UserAppMapper;
import com.knowflow.store.EconomyError;
import com.knowflow.vo.UserAppVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户应用状态服务（Architecture-3，§27/§38）。
 * Backend user_app = 真源；只允许操作当前用户（SecurityContext 传入，§33/§256-260）。
 * 幂等 upsert（§36-38）；非法 appKey 拒绝（§34-35/§345）；非法状态组合归一化（§266-267）。
 */
@Service
@RequiredArgsConstructor
public class UserAppService {

    private final UserAppMapper userAppMapper;
    private final OfficialAppRegistry registry;

    /** 查询当前用户全部 App 状态；首次访问自动补齐默认安装（幂等，§40/§412）。 */
    @Transactional
    public List<UserAppVO> getUserApps(Long userId) {
        initializeDefaults(userId);
        return userAppMapper.selectList(new LambdaQueryWrapper<UserApp>()
                        .eq(UserApp::getUserId, userId)
                        .orderByAsc(UserApp::getSortOrder)
                        .orderByAsc(UserApp::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 新用户默认安装（flashcard/quiz/mistake/focus，§39）；幂等：重复调用不产生重复记录（§40）。 */
    @Transactional
    public void initializeDefaults(Long userId) {
        List<UserApp> existing = userAppMapper.selectList(new LambdaQueryWrapper<UserApp>()
                .eq(UserApp::getUserId, userId));
        for (String key : OfficialAppRegistry.DEFAULT_APP_KEYS) {
            boolean exists = existing.stream().anyMatch(u -> key.equals(u.getAppKey()));
            if (!exists) {
                UserApp app = new UserApp();
                app.setUserId(userId);
                app.setAppKey(key);
                app.setInstalled(1);
                app.setEnabled(1);
                app.setSortOrder(OfficialAppRegistry.DEFAULT_APP_KEYS.indexOf(key));
                try {
                    userAppMapper.insert(app);
                } catch (DuplicateKeyException e) {
                    // 并发初始化：uk_user_app 兜底，忽略
                }
            }
        }
    }

    /**
     * 更新用户 App 状态（幂等 upsert，§36-38）。
     * 校验：appKey 必须在官方白名单（§124/§345）；installed=false 强制 enabled=false（§267）。
     */
    @Transactional
    public UserAppVO upsert(Long userId, String appKey, Boolean installed, Boolean enabled, Integer sortOrder) {
        if (!registry.isValid(appKey)) {
            throw new BusinessException(EconomyError.INVALID_APP, "无效的应用标识");
        }
        boolean inst = installed == null || installed; // 缺省视为 true（安装）
        boolean enab = enabled == null || enabled;
        if (!inst) {
            enab = false; // 非法状态组合归一化：未安装必为停用（§266-267）
        }
        int order = sortOrder != null ? Math.max(0, sortOrder) : 0;

        UserApp existing = userAppMapper.selectOne(new LambdaQueryWrapper<UserApp>()
                .eq(UserApp::getUserId, userId)
                .eq(UserApp::getAppKey, appKey));
        if (existing != null) {
            existing.setInstalled(inst ? 1 : 0);
            existing.setEnabled(enab ? 1 : 0);
            existing.setSortOrder(order);
            userAppMapper.updateById(existing);
            return toVO(existing);
        }
        UserApp created = new UserApp();
        created.setUserId(userId);
        created.setAppKey(appKey);
        created.setInstalled(inst ? 1 : 0);
        created.setEnabled(enab ? 1 : 0);
        created.setSortOrder(order);
        try {
            userAppMapper.insert(created);
        } catch (DuplicateKeyException e) {
            UserApp again = userAppMapper.selectOne(new LambdaQueryWrapper<UserApp>()
                    .eq(UserApp::getUserId, userId)
                    .eq(UserApp::getAppKey, appKey));
            again.setInstalled(inst ? 1 : 0);
            again.setEnabled(enab ? 1 : 0);
            again.setSortOrder(order);
            userAppMapper.updateById(again);
            return toVO(again);
        }
        return toVO(created);
    }

    /** 购买成功后自动安装启用（PurchaseService 同事务调用，§270-274）。 */
    @Transactional
    public void markInstalledAndEnabled(Long userId, String appKey) {
        upsert(userId, appKey, true, true, null);
    }

    private UserAppVO toVO(UserApp u) {
        UserAppVO vo = new UserAppVO();
        vo.setAppKey(u.getAppKey());
        vo.setInstalled(u.getInstalled() != null && u.getInstalled() == 1);
        vo.setEnabled(u.getEnabled() != null && u.getEnabled() == 1);
        vo.setSortOrder(u.getSortOrder() != null ? u.getSortOrder() : 0);
        return vo;
    }
}
