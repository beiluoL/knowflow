package com.knowflow.interview.service.career.discover;

import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.knowflow.interview.career.discover.HttpJson;
import com.knowflow.interview.career.discover.JobModels;
import com.knowflow.interview.career.discover.JobSourceAdapter;
import com.knowflow.interview.career.discover.JobSourceCache;
import com.knowflow.interview.career.discover.JobSourceRegistry;
/**
 * 多源岗位搜索。
 *
 * <p>失败语义：单个来源超时、非 2xx、结构变化或限频，都只影响该来源，
 * 其余来源照常返回；响应里用 `sources[].status` 说明，并置 `partial=true`。
 * 绝不因为来源失效而编造岗位填补结果。</p>
 */
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final JobSourceRegistry registry;
    private final JobSourceCache cache;

    public JobModels.SearchResult search(JobModels.JobQuery query) {
        List<JobModels.NormalizedJob> merged = new ArrayList<>();
        List<JobModels.SourceStatus> statuses = new ArrayList<>();
        LinkedHashSet<String> attribution = new LinkedHashSet<>();
        boolean partial = false;

        List<JobSourceAdapter> adapters = registry.enabledFor(query);
        if (adapters.isEmpty()) {
            throw new BusinessException(400, "没有可用的岗位来源，请检查筛选条件或来源配置");
        }

        for (JobSourceAdapter adapter : adapters) {
            JobModels.JobSourcePolicy policy = adapter.policy();
            String cacheKey = query.cacheKey();
            Optional<List<JobModels.NormalizedJob>> cached = cache.find(adapter.id(), cacheKey)
                    .map(entry -> entry.items());

            if (cache.isFresh(cache.find(adapter.id(), cacheKey), policy) && cached.isPresent()) {
                merged.addAll(cached.get());
                statuses.add(new JobModels.SourceStatus(adapter.id(), adapter.displayName(),
                        "SUCCESS", false, null, cached.get().size()));
                if (policy.attributionRequired()) attribution.add(policy.attributionText());
                continue;
            }

            if (!cache.canFetch(adapter.id(), policy)) {
                // 限频：有旧缓存就返回旧数据并标记 stale，没有就明确告知，不伪造空结果以外的内容
                if (cached.isPresent()) {
                    merged.addAll(cached.get());
                    statuses.add(new JobModels.SourceStatus(adapter.id(), adapter.displayName(),
                            "THROTTLED", true, "该来源请求过于频繁，展示的是稍早的结果", cached.get().size()));
                    partial = true;
                } else {
                    statuses.add(new JobModels.SourceStatus(adapter.id(), adapter.displayName(),
                            "THROTTLED", false, "该来源请求过于频繁，请稍后再试", 0));
                    partial = true;
                }
                if (policy.attributionRequired() && cached.isPresent()) attribution.add(policy.attributionText());
                continue;
            }

            try {
                cache.markFetched(adapter.id());
                List<JobModels.NormalizedJob> items = adapter.search(query);
                cache.put(adapter.id(), cacheKey, items);
                merged.addAll(items);
                statuses.add(new JobModels.SourceStatus(adapter.id(), adapter.displayName(),
                        "SUCCESS", false, null, items.size()));
                if (policy.attributionRequired()) attribution.add(policy.attributionText());
            } catch (HttpJson.UpstreamException failure) {
                partial = true;
                if (cached.isPresent()) {
                    merged.addAll(cached.get());
                    statuses.add(new JobModels.SourceStatus(adapter.id(), adapter.displayName(),
                            "DEGRADED", true, failure.getMessage() + "，展示的是稍早的结果", cached.get().size()));
                    if (policy.attributionRequired()) attribution.add(policy.attributionText());
                } else {
                    statuses.add(new JobModels.SourceStatus(adapter.id(), adapter.displayName(),
                            "FAILED", false, failure.getMessage(), 0));
                }
            } catch (RuntimeException failure) {
                // 结构变化等非预期异常：同样只降级当前来源，不泄漏堆栈
                partial = true;
                if (cached.isPresent()) {
                    merged.addAll(cached.get());
                    statuses.add(new JobModels.SourceStatus(adapter.id(), adapter.displayName(),
                            "DEGRADED", true, "该来源返回结构异常，展示的是稍早的结果", cached.get().size()));
                } else {
                    statuses.add(new JobModels.SourceStatus(adapter.id(), adapter.displayName(),
                            "FAILED", false, "该来源返回结构异常，请稍后再试", 0));
                }
            }
        }

        List<JobModels.NormalizedJob> deduped = dedupe(merged);
        deduped.sort(Comparator
                .comparing(JobModels.NormalizedJob::postedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(job -> job.title() == null ? "" : job.title()));

        int total = deduped.size();
        int from = Math.min((query.page() - 1) * query.size(), total);
        int to = Math.min(from + query.size(), total);
        List<JobModels.NormalizedJob> page = deduped.subList(from, to);

        return new JobModels.SearchResult(page, List.copyOf(statuses), partial,
                List.copyOf(attribution), query.page(), query.size(), total);
    }

    /**
     * 三层去重：来源+外部 ID → 规范化 URL → 公司+标题+城市（仅标记疑似，不删除）。
     */
    private List<JobModels.NormalizedJob> dedupe(List<JobModels.NormalizedJob> jobs) {
        Map<String, JobModels.NormalizedJob> byKey = new LinkedHashMap<>();
        Map<String, String> softSeen = new LinkedHashMap<>();
        for (JobModels.NormalizedJob job : jobs) {
            if (job.title() == null || job.title().isBlank()) continue;
            String key = job.dedupeKey();
            if (byKey.containsKey(key)) continue;
            String soft = job.softKey();
            if (softSeen.containsKey(soft) && !soft.isBlank()) {
                // 疑似重复：保留第一条，但在 freshness 上标注，让用户自己判断
                JobModels.NormalizedJob kept = byKey.get(softSeen.get(soft));
                if (kept != null) {
                    byKey.put(softSeen.get(soft), withFreshness(kept, "POSSIBLE_DUPLICATE"));
                }
                continue;
            }
            softSeen.put(soft, key);
            byKey.put(key, job);
        }
        return new ArrayList<>(byKey.values());
    }

    private JobModels.NormalizedJob withFreshness(JobModels.NormalizedJob job, String freshness) {
        return new JobModels.NormalizedJob(job.sourceId(), job.sourceName(), job.externalId(), job.title(),
                job.company(), job.location(), job.salaryText(), job.salaryMin(), job.salaryMax(),
                job.salaryCurrency(), job.salaryPeriod(), job.experience(), job.education(), job.jobType(),
                job.remoteType(), job.description(), job.tags(), job.requiredSkills(), job.preferredSkills(),
                job.sourceUrl(), job.applicationUrl(), job.postedAt(), job.fetchedAt(), job.attribution(), freshness);
    }
}
