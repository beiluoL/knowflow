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
        migrateCareerApps(userId);
        return userAppMapper.selectList(new LambdaQueryWrapper<UserApp>()
                        .eq(UserApp::getUserId, userId)
                        .orderByAsc(UserApp::getSortOrder)
                        .orderByAsc(UserApp::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * Career V1.0 → V2.0 幂等兼容迁移（Career Apps V2.0 §4.4）。
     *
     * <p>规则（不猜测用户意图）：
     * <ul>
     *   <li>只有「已安装 career-workbench」的用户才补偿五个业务中心应用；</li>
     *   <li>只补齐**从未存在过记录**的应用，已有记录（含显式停用）一律不覆盖；</li>
     *   <li>补偿应用继承工作台的 enabled 状态：工作台停用则子应用也停用，不擅自让菜单重新出现；</li>
     *   <li>简历工具箱 / 简历模板不参与补偿，必须用户主动获取；</li>
     *   <li>工作台 sortOrder 仅在仍为默认 0 时归一到第 6 位，保持产品菜单顺序，不覆盖用户自定义排序。</li>
     * </ul>
     * 重复执行无副作用；并发插入由 uk_user_app 兜底。
     */
    @Transactional
    public void migrateCareerApps(Long userId) {
        List<UserApp> existing = userAppMapper.selectList(new LambdaQueryWrapper<UserApp>()
                .eq(UserApp::getUserId, userId));
        UserApp workbench = existing.stream()
                .filter(u -> "career-workbench".equals(u.getAppKey()))
                .findFirst().orElse(null);
        if (workbench == null || workbench.getInstalled() == null || workbench.getInstalled() != 1) return;

        boolean enabled = workbench.getEnabled() != null && workbench.getEnabled() == 1;
        boolean insertedAny = false;
        for (int i = 0; i < OfficialAppRegistry.CAREER_COMPANION_KEYS.size(); i++) {
            String key = OfficialAppRegistry.CAREER_COMPANION_KEYS.get(i);
            if (existing.stream().anyMatch(u -> key.equals(u.getAppKey()))) continue;
            UserApp app = new UserApp();
            app.setUserId(userId);
            app.setAppKey(key);
            app.setInstalled(1);
            app.setEnabled(enabled ? 1 : 0);
            app.setSortOrder(i + 1);
            try {
                userAppMapper.insert(app);
                existing.add(app);
                insertedAny = true;
            } catch (DuplicateKeyException concurrent) {
                // 并发迁移：另一请求已插入，保持其状态
            }
        }
        if (insertedAny && workbench.getSortOrder() != null && workbench.getSortOrder() == 0) {
            workbench.setSortOrder(6);
            userAppMapper.updateById(workbench);
        }
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
