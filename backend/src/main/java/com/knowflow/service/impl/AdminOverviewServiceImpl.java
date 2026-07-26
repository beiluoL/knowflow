package com.knowflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.ChatConversation;
import com.knowflow.entity.CommunityPost;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.ChatConversationMapper;
import com.knowflow.mapper.CommunityPostMapper;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.AdminOverviewService;
import com.knowflow.vo.AdminOverviewVO;
import com.knowflow.vo.HealthMetric;
import com.knowflow.vo.RecentActivity;
import com.knowflow.vo.UserGrowthPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 管理员概览统计 Service 实现。
 * 统计逻辑下沉到 Service 层，Controller 不再直接注入 Mapper；所有指标均基于真实表数据统计。
 */
@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {

    private final SysUserMapper userMapper;
    private final DocDocumentMapper docMapper;
    private final DocCategoryMapper categoryMapper;
    private final ChatConversationMapper conversationMapper;
    private final LearningPathMapper pathMapper;
    private final DocReadProgressMapper readProgressMapper;
    private final CommunityPostMapper postMapper;

    // 索引 1~7 对应 DayOfWeek.MONDAY~SUNDAY（getValue() 返回 1 表示周一）
    private static final String[] WEEKDAY_CN = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @Override
    public AdminOverviewVO getOverview() {
        AdminOverviewVO vo = new AdminOverviewVO();
        // 总量统计（deleted=0 条件由 BaseEntity 的 @TableLogic 自动追加，无需手写）
        vo.setTotalUsers(userMapper.selectCount(null));
        vo.setTotalDocs(docMapper.selectCount(null));
        vo.setTotalCategories(categoryMapper.selectCount(null));
        vo.setTotalConversations(conversationMapper.selectCount(null));
        vo.setTotalLearningPaths(pathMapper.selectCount(null));

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);

        vo.setTodayNewUsers(userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .ge(SysUser::getCreateTime, startOfDay)
                .lt(SysUser::getCreateTime, startOfNextDay)));
        vo.setTodayNewDocs(docMapper.selectCount(new LambdaQueryWrapper<DocDocument>()
                .ge(DocDocument::getCreateTime, startOfDay)
                .lt(DocDocument::getCreateTime, startOfNextDay)));
        long todayActive = countTodayActiveUsers(startOfDay, startOfNextDay);
        vo.setTodayActiveUsers(todayActive);

        // 平台起始日期：最早注册用户的时间，用于展示运营时长
        SysUser firstUser = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getCreateTime).last("LIMIT 1"));
        vo.setFirstUserDate(firstUser != null && firstUser.getCreateTime() != null
                ? firstUser.getCreateTime().toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : "—");

        vo.setUserGrowth(buildUserGrowth(today));
        vo.setHealthMetrics(buildHealthMetrics(todayActive, vo.getTotalUsers()));
        vo.setRecentActivities(buildRecentActivities());
        return vo;
    }

    /** 统计最近 7 天（含今日）的用户增长：每日新增与累计总数。 */
    private List<UserGrowthPoint> buildUserGrowth(LocalDate today) {
        List<UserGrowthPoint> growth = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            long newUsers = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .ge(SysUser::getCreateTime, start)
                    .lt(SysUser::getCreateTime, end));
            long total = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .lt(SysUser::getCreateTime, end));
            UserGrowthPoint point = new UserGrowthPoint();
            int dow = day.getDayOfWeek().getValue();
            point.setDay(WEEKDAY_CN[dow]);
            point.setNewUsers(newUsers);
            point.setTotalUsers(total);
            growth.add(point);
        }
        return growth;
    }

    /** 基于真实业务数据计算内容健康度指标。 */
    private List<HealthMetric> buildHealthMetrics(long todayActive, long totalUsers) {
        List<HealthMetric> metrics = new ArrayList<>();
        long totalDocs = docMapper.selectCount(null);
        long publishedDocs = docMapper.selectCount(new LambdaQueryWrapper<DocDocument>().eq(DocDocument::getStatus, 1));
        metrics.add(percentMetric("文档发布率",
                totalDocs == 0 ? 100 : (int) (publishedDocs * 100 / totalDocs),
                "已发布 " + publishedDocs + " / 共 " + totalDocs + " 篇", "file-check"));

        long totalCategories = categoryMapper.selectCount(null);
        long coveredCategories = categoryMapper.selectCount(new LambdaQueryWrapper<DocCategory>().gt(DocCategory::getDocCount, 0));
        metrics.add(percentMetric("分类文档覆盖率",
                totalCategories == 0 ? 100 : (int) (coveredCategories * 100 / totalCategories),
                "有文档的分类 " + coveredCategories + " / " + totalCategories, "folder-tree"));

        metrics.add(percentMetric("今日活跃率",
                totalUsers == 0 ? 0 : (int) (todayActive * 100 / totalUsers),
                "今日活跃 " + todayActive + " / " + totalUsers + " 用户", "activity"));

        long totalRead = readProgressMapper.selectCount(null);
        long completedRead = readProgressMapper.selectCount(new LambdaQueryWrapper<DocReadProgress>()
                .ge(DocReadProgress::getProgress, 100));
        metrics.add(percentMetric("阅读完成率",
                totalRead == 0 ? 0 : (int) (completedRead * 100 / totalRead),
                "完成阅读 " + completedRead + " / " + totalRead + " 条进度", "book-open"));
        return metrics;
    }

    private HealthMetric percentMetric(String label, int value, String detail, String icon) {
        HealthMetric m = new HealthMetric();
        m.setLabel(label);
        m.setValue(value);
        m.setDetail(detail);
        m.setIcon(icon);
        m.setLevel(value >= 80 ? "good" : (value >= 50 ? "warn" : "bad"));
        return m;
    }

    /** 合并社区发帖与用户注册作为最近活动流（各取最新 3 条合并展示）。 */
    private List<RecentActivity> buildRecentActivities() {
        List<RecentActivity> posts = postMapper.selectList(new LambdaQueryWrapper<CommunityPost>()
                        .orderByDesc(CommunityPost::getCreateTime).last("LIMIT 3"))
                .stream().map(post -> {
                    RecentActivity a = new RecentActivity();
                    a.setId(post.getId());
                    SysUser author = userMapper.selectById(post.getUserId());
                    a.setUserName(author != null ? StrUtil.blankToDefault(author.getNickname(), author.getUsername()) : "用户");
                    String title = post.getTitle() != null && post.getTitle().length() > 20
                            ? post.getTitle().substring(0, 20) + "…" : post.getTitle();
                    a.setAction("发布了帖子《" + title + "》");
                    a.setTime(relativeTime(post.getCreateTime()));
                    a.setType("post");
                    return a;
                }).collect(Collectors.toList());

        List<RecentActivity> registers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .orderByDesc(SysUser::getCreateTime).last("LIMIT 3"))
                .stream().map(u -> {
                    RecentActivity a = new RecentActivity();
                    a.setId(u.getId());
                    a.setUserName(StrUtil.blankToDefault(u.getNickname(), u.getUsername()));
                    a.setAction("注册了账号");
                    a.setTime(relativeTime(u.getCreateTime()));
                    a.setType("register");
                    return a;
                }).collect(Collectors.toList());

        List<RecentActivity> merged = new ArrayList<>();
        merged.addAll(posts);
        merged.addAll(registers);
        return merged;
    }

    /** 将时间格式化为相对描述（中文）。 */
    private String relativeTime(LocalDateTime time) {
        if (time == null) {
            return "未知";
        }
        long minutes = java.time.Duration.between(time, LocalDateTime.now()).toMinutes();
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + " 分钟前";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " 小时前";
        }
        long days = hours / 24;
        if (days < 30) {
            return days + " 天前";
        }
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * 统计今日活跃用户数：今日在 chat_conversation 或 doc_read_progress
     * 表中存在记录的 user_id 去重后的数量。
     *
     * @param startOfDay     今日起始时间（含）
     * @param startOfNextDay 次日起始时间（不含）
     * @return 今日活跃用户数
     */
    private long countTodayActiveUsers(LocalDateTime startOfDay, LocalDateTime startOfNextDay) {
        // 只查 user_id 字段，避免 SELECT *（阿里规范：查询务必指明字段）
        List<ChatConversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ChatConversation>()
                        .select(ChatConversation::getUserId)
                        .ge(ChatConversation::getCreateTime, startOfDay)
                        .lt(ChatConversation::getCreateTime, startOfNextDay));
        List<DocReadProgress> progresses = readProgressMapper.selectList(
                new LambdaQueryWrapper<DocReadProgress>()
                        .select(DocReadProgress::getUserId)
                        .ge(DocReadProgress::getCreateTime, startOfDay)
                        .lt(DocReadProgress::getCreateTime, startOfNextDay));

        Set<Long> activeUserIds = Stream.concat(
                conversations.stream().map(ChatConversation::getUserId),
                progresses.stream().map(DocReadProgress::getUserId)
        ).filter(Objects::nonNull).collect(Collectors.toSet());
        return activeUserIds.size();
    }
}
