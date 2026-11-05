package com.knowflow.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.exam.contract.RecentExamActivityQuery;
import com.knowflow.exam.entity.ExamAttempt;
import com.knowflow.exam.mapper.ExamAttemptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Keeps the existing bounded activity query within its data-owning module. */
@Service
@RequiredArgsConstructor
public class RecentExamActivityQueryService implements RecentExamActivityQuery {
    private final ExamAttemptMapper records;

    /** {@inheritDoc} */
    @Override
    public java.util.List<String> statuses(long ownerId) {
        var rows = records.selectList(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getUserId, ownerId)
                .orderByDesc(ExamAttempt::getCreateTime).last("LIMIT 3"));
        return rows.stream().map(row -> java.util.Objects.toString(row.getStatus(), "UNKNOWN")).toList();
    }
}
