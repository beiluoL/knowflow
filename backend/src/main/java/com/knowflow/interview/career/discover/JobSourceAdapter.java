package com.knowflow.interview.career.discover;

import java.util.List;

import com.knowflow.interview.service.career.discover.JobSearchService;
/**
 * 岗位数据源适配器。
 *
 * <p>每个实现只负责「调用上游 + 归一化字段」，不负责缓存、限频、去重与降级——
 * 那些是 {@link JobSearchService} 的职责，避免每个适配器各写一套。</p>
 */
public interface JobSourceAdapter {

    /** 稳定来源标识，用于政策、缓存键与前端展示。 */
    String id();

    /** 展示名（中文，不含营销词）。 */
    String displayName();

    /** 该来源的政策与频率配置。 */
    JobModels.JobSourcePolicy policy();

    /** 当前查询是否适用于该来源（如企业板需要 company token）。 */
    default boolean supports(JobModels.JobQuery query) {
        return true;
    }

    /**
     * 执行搜索。
     *
     * <p>实现必须只返回真实上游数据；字段缺失时返回 null，禁止猜测薪资、年限或学历。</p>
     */
    List<JobModels.NormalizedJob> search(JobModels.JobQuery query);
}
