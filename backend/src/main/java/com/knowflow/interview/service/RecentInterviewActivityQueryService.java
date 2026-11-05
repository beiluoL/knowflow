package com.knowflow.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.interview.contract.RecentInterviewActivityQuery;
import com.knowflow.interview.entity.MockInterviewSession;
import com.knowflow.interview.mapper.MockInterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 在数据所属模块内保留现有的有界活动查询。 */
@Service
@RequiredArgsConstructor
public class RecentInterviewActivityQueryService implements RecentInterviewActivityQuery {
    private final MockInterviewSessionMapper records;

    /** {@inheritDoc} */
    @Override
    public int boundedCount(long ownerId) {
        var rows = records.selectList(new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getUserId, ownerId)
                .orderByDesc(MockInterviewSession::getCreateTime).last("LIMIT 3"));
        return rows.size();
    }
}
