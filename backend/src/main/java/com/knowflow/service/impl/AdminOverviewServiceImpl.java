package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.ChatConversation;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.ChatConversationMapper;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.AdminOverviewService;
import com.knowflow.vo.AdminOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 管理员概览统计 Service 实现。
 * 统计逻辑下沉到 Service 层，Controller 不再直接注入 Mapper。
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

    @Override
    public AdminOverviewVO getOverview() {
        AdminOverviewVO vo = new AdminOverviewVO();
        // 总量统计（deleted=0 条件由 BaseEntity 的 @TableLogic 自动追加，无需手写）
        vo.setTotalUsers(userMapper.selectCount(null));
        vo.setTotalDocs(docMapper.selectCount(null));
        vo.setTotalCategories(categoryMapper.selectCount(null));
        vo.setTotalConversations(conversationMapper.selectCount(null));
        vo.setTotalLearningPaths(pathMapper.selectCount(null));

        // 今日维度：日期边界用 java.time 计算，禁止硬编码日期字符串
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);

        // 今日新增用户：sys_user.create_time 落在今日
        vo.setTodayNewUsers(userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .ge(SysUser::getCreateTime, startOfDay)
                .lt(SysUser::getCreateTime, startOfNextDay)));

        // 今日新增文档：doc_document.create_time 落在今日
        vo.setTodayNewDocs(docMapper.selectCount(new LambdaQueryWrapper<DocDocument>()
                .ge(DocDocument::getCreateTime, startOfDay)
                .lt(DocDocument::getCreateTime, startOfNextDay)));

        // 今日活跃用户：今日有 chat_conversation 或 doc_read_progress 记录的用户去重数
        vo.setTodayActiveUsers(countTodayActiveUsers(startOfDay, startOfNextDay));

        return vo;
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
