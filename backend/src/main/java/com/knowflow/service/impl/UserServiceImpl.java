package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.LoginDTO;
import com.knowflow.dto.RegisterDTO;
import com.knowflow.dto.UpdateProfileDTO;
import com.knowflow.entity.DocFavorite;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningTask;
import com.knowflow.entity.LearningUserPath;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocFavoriteMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningTaskMapper;
import com.knowflow.mapper.LearningUserPathMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.UserService;
import com.knowflow.utils.JwtUtils;
import com.knowflow.vo.LoginVO;
import com.knowflow.vo.UserStatsVO;
import com.knowflow.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户业务服务实现。 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final DocReadProgressMapper readProgressMapper;
    private final DocFavoriteMapper favoriteMapper;
    private final LearningUserPathMapper userPathMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final LearningTaskMapper taskMapper;
    /** Architecture-2：XP/等级统一从钱包读取（修 G1：消除 stats 推导公式口径分裂，§11/§131） */
    private final com.knowflow.reward.WalletService walletService;

    /**
     * 逻辑删除用户时释放唯一键所用的内部归档前缀（18 字符）。
     *
     * <p>归档值形如 {@code #__deleted_user__#<id>#<原值>}：</p>
     * <ul>
     *   <li>含主键 id ⇒ 全表唯一，不与任何活跃行冲突，且同一行重复释放幂等；</li>
     *   <li>公开用户名最短 3 字符，因此完整归档值最短 23 字符（单数字 id）&gt; 注册上限 20
     *       ⇒ 公开注册无法构造出与该归档值同名的用户名；</li>
     *   <li>用户名保留原值便于审计；邮箱归档为不符合公开邮箱格式的内部值，防止被预占。</li>
     * </ul>
     */
    private static final String RELEASED_KEY_PREFIX = "#__deleted_user__#";

    /** sys_user.username 列宽。 */
    private static final int USERNAME_MAX_LENGTH = 50;

    /** 登录校验：用户名或密码错误统一返回 401，以防止用户名枚举。 */
    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        // F-04 修复：登录失败返回 HTTP 401；统一提示语防止用户名枚举
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(BeanUtil.copyProperties(user, UserVO.class));
        return vo;
    }

    /**
     * 注册新用户。
     *
     * <p><b>P2-User-Register-After-Delete</b>：唯一性判定必须区分两类占位者——</p>
     * <ol>
     *   <li><b>活跃行</b>占位 ⇒ 409「已存在」：活跃用户之间 username / email 仍严格互斥；</li>
     *   <li><b>逻辑删除行</b>占位 ⇒ 先释放其唯一键再放行（老用户于本轮修复前被删除，
     *       username / email 仍原样占着唯一键），否则 {@code save} 会撞唯一索引 → HTTP 500。</li>
     * </ol>
     *
     * <p>无论哪种情况，注册都<b>只创建全新 userId</b>，绝不复活被删除的历史行，
     * 因此新用户不会继承旧身份的任何业务数据。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        SysUser existUser = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (existUser != null) {
            throw new BusinessException(409, "用户名已存在");
        }
        if (StrUtil.isNotBlank(dto.getEmail())) {
            SysUser existEmail = this.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, dto.getEmail()));
            if (existEmail != null) {
                throw new BusinessException(409, "邮箱已被注册");
            }
        }
        // 释放逻辑删除历史行占用的唯一键槽位（幂等；对活跃行无任何影响）
        releaseDeletedKeyHolders(dto.getUsername(), dto.getEmail());

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StrUtil.isNotBlank(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setRole("USER");
        user.setTotalStudyHours(BigDecimal.ZERO);
        user.setReadDocsCount(0);
        user.setStreakDays(0);
        user.setFavoriteCount(0);
        user.setLevel(1);
        user.setExp(0);
        user.setEnergy(100);
        try {
            this.save(user);
        } catch (DuplicateKeyException e) {
            // 并发注册同一 username / email：唯一索引是最后一道防线。
            // 这里把它翻译成明确的业务错误（而不是 DuplicateKeyException → HTTP 500），
            // 事务整体回滚，库中不会留下任何半成品行。
            throw new BusinessException(409, "用户名或邮箱已被注册");
        }
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(BeanUtil.copyProperties(user, UserVO.class));
        return vo;
    }

    /**
     * 逻辑删除用户，并在同一事务内<b>释放其占用的业务唯一键</b>（username / email）。
     *
     * <p><b>为什么必须在删除时释放</b>：{@code sys_user.username} 是列级 UNIQUE、
     * {@code email} 是 {@code uk_user_email}，两个唯一键都<b>不含 deleted 列</b>。
     * 若删除只把 {@code deleted} 置 1，历史行会永久占死这两个键 —— 此后再用相同
     * username / email 注册必然撞唯一索引 → {@code DuplicateKeyException} → HTTP 500，
     * 表现为「该身份永久无法再注册」。而 {@code POST /api/auth/register} 是<b>公开无鉴权</b>端点，
     * 影响面不限于管理员。</p>
     *
     * <p><b>为什么不复活原行</b>：删除流程只翻转 {@code deleted}，不做任何关联清理；
     * 库内 52 张业务表以 {@code user_id} 关联用户。复活旧主键会让新注册者继承旧身份的
     * 学习路径 / 闪卡 / 任务 / 私信 / 收藏 / 社群 / 通知 / 钱包等全部历史数据。
     * 因此这里<b>只交还键位</b>，注册侧始终创建全新 userId（身份主体不可复用，与
     * {@code doc_favorite} 这类「关系行复活」的语义有本质区别）。</p>
     *
     * @param id 待删除用户主键
     * @return 是否删除成功（语义与 {@code IService#removeById} 一致）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        SysUser target = id == null ? null : this.getById(id);
        if (target != null) {
            releaseUniqueKeys(target, LocalDateTime.now());
        }
        return super.removeById(id);
    }

    /**
     * 释放「已逻辑删除」历史行对指定 username / email 的唯一键占用（幂等）。
     *
     * <p>覆盖两类历史行：本轮修复<b>之后</b>被删除的用户（删除时已归档，此处查不到），
     * 以及本轮修复<b>之前</b>被删除的用户（username / email 仍是原值）。</p>
     */
    private void releaseDeletedKeyHolders(String username, String email) {
        Map<Long, SysUser> holders = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(username)) {
            for (SysUser holder : baseMapper.selectDeletedHoldersByUsername(username)) {
                holders.put(holder.getId(), holder);
            }
        }
        if (StrUtil.isNotBlank(email)) {
            for (SysUser holder : baseMapper.selectDeletedHoldersByEmail(email)) {
                holders.putIfAbsent(holder.getId(), holder);
            }
        }
        if (holders.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 所有事务都按主键升序更新，避免交叉复用 username/email 时形成相反的行锁顺序。
        List<SysUser> orderedHolders = holders.values().stream()
                .sorted(Comparator.comparing(SysUser::getId))
                .toList();
        for (SysUser holder : orderedHolders) {
            releaseUniqueKeys(holder, now);
        }
    }

    /** 把某用户的 username / email 改写为内部归档值，交还唯一键槽位。 */
    private void releaseUniqueKeys(SysUser holder, LocalDateTime updateTime) {
        baseMapper.releaseUniqueKeys(holder.getId(), archivedUsername(holder), archivedEmail(holder), updateTime);
    }

    /**
     * 归档用户名：{@code #__deleted_user__#<id>#<原用户名>}，按列宽 50 截断。
     * 已归档（前缀命中）则原样返回，保证重复释放幂等、不会二次拼接。
     */
    private String archivedUsername(SysUser holder) {
        String current = holder.getUsername();
        String head = RELEASED_KEY_PREFIX + holder.getId() + "#";
        if (current != null && current.length() > 20 && current.startsWith(head)) {
            return current;
        }
        String tail = current == null ? "" : current;
        int room = Math.max(USERNAME_MAX_LENGTH - head.length(), 0);
        return head + (tail.length() > room ? tail.substring(0, room) : tail);
    }

    /**
     * 归档邮箱使用仅含内部前缀与主键的非邮箱值，避免匿名注册者预先占用未来归档值；
     * 原邮箱为空时返回 {@code null} —— 唯一索引中的 NULL 不占用槽位（H2 与 MySQL 一致）。
     */
    private String archivedEmail(SysUser holder) {
        String current = holder.getEmail();
        if (StrUtil.isBlank(current)) {
            return null;
        }
        String archived = RELEASED_KEY_PREFIX + holder.getId();
        return archived.equals(current) ? current : archived;
    }


    @Override
    public UserVO getCurrentUser(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    @Override
    public UserStatsVO getUserStats(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 所有统计均基于真实业务表实时聚合，避免存储字段漂移导致「死数据」
        int readDocsCount = Math.toIntExact(readProgressMapper.selectCount(
                new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getUserId, userId)));
        int favoriteCount = Math.toIntExact(favoriteMapper.selectCount(
                new LambdaQueryWrapper<DocFavorite>().eq(DocFavorite::getUserId, userId)));
        int completedPaths = Math.toIntExact(userPathMapper.selectCount(
                new LambdaQueryWrapper<LearningUserPath>()
                        .eq(LearningUserPath::getUserId, userId)
                        .ge(LearningUserPath::getProgress, new BigDecimal(100))));

        List<LearningUserPath> enrolled = userPathMapper.selectList(
                new LambdaQueryWrapper<LearningUserPath>().eq(LearningUserPath::getUserId, userId));
        long totalFlashcards;
        if (enrolled.isEmpty()) {
            totalFlashcards = 0;
        } else {
            List<Long> pathIds = enrolled.stream().map(LearningUserPath::getPathId).collect(Collectors.toList());
            totalFlashcards = flashcardMapper.selectCount(
                    new LambdaQueryWrapper<LearningFlashcard>().in(LearningFlashcard::getPathId, pathIds));
        }

        // Architecture-2：等级与经验统一读取 Wallet（Wallet = 当前状态；首次访问惰性初始化 xp=sys_user.exp，§7/§11）
        com.knowflow.entity.Wallet wallet = walletService.getOrCreate(userId);
        int exp = wallet.getXp();
        int level = wallet.getLevel();
        // 精力值：待完成任务越多消耗越多，下限 0 上限 100（energy 不在经济闭环内，保持兼容，§9/§12）
        long pendingTasks = taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .eq(LearningTask::getStatus, 0));
        int energy = Math.max(0, Math.min(100, 100 - (int) pendingTasks * 10));

        UserStatsVO stats = new UserStatsVO();
        stats.setUserId(userId);
        stats.setTotalStudyHours(user.getTotalStudyHours());
        stats.setReadDocsCount(readDocsCount);
        stats.setStreakDays(user.getStreakDays());
        stats.setFavoriteCount(favoriteCount);
        stats.setLevel(level);
        stats.setExp(exp);
        stats.setEnergy(energy);
        stats.setCompletedPaths(completedPaths);
        stats.setTotalFlashcards((int) totalFlashcards);
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            SysUser existEmail = this.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, dto.getEmail())
                    .ne(SysUser::getId, userId));
            if (existEmail != null) {
                throw new BusinessException(409, "邮箱已被使用");
            }
            // 同一唯一键 uk_user_email：逻辑删除历史行仍可能占位 → 先释放，否则 updateById 撞唯一索引 → 500
            releaseDeletedKeyHolders(null, dto.getEmail());
            user.setEmail(dto.getEmail());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        this.updateById(user);
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    /**
     * 第三方 OAuth 登录：provider + providerUid 唯一确定一个社交账号。
     * 首次登录自动注册：用户名格式「{provider}_{providerUid}」，密码留空（不可用于账号密码登录）。
     */
    @Override
    public LoginVO oauthLogin(String provider, String providerUid, String nickname, String avatar, String email) {
        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getProvider, provider)
                .eq(SysUser::getProviderUid, providerUid));
        if (user == null) {
            // 自动注册新用户
            user = new SysUser();
            user.setUsername(provider + "_" + providerUid);
            user.setPassword(""); // 社交登录用户无密码
            user.setNickname(StrUtil.isNotBlank(nickname) ? nickname : user.getUsername());
            user.setAvatar(avatar);
            user.setEmail(email);
            user.setProvider(provider);
            user.setProviderUid(providerUid);
            user.setRole("USER");
            user.setTotalStudyHours(BigDecimal.ZERO);
            user.setReadDocsCount(0);
            user.setStreakDays(0);
            user.setFavoriteCount(0);
            user.setLevel(1);
            user.setExp(0);
            user.setEnergy(100);
            this.save(user);
        } else {
            // 已存在则更新头像与昵称（社交平台信息可能变更）
            boolean dirty = false;
            if (StrUtil.isNotBlank(avatar) && !avatar.equals(user.getAvatar())) {
                user.setAvatar(avatar);
                dirty = true;
            }
            if (StrUtil.isNotBlank(nickname) && !nickname.equals(user.getNickname())) {
                user.setNickname(nickname);
                dirty = true;
            }
            if (dirty) this.updateById(user);
        }
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(BeanUtil.copyProperties(user, UserVO.class));
        return vo;
    }
}
