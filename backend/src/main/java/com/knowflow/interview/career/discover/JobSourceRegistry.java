package com.knowflow.interview.career.discover;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.interview.career.discover.adapters.AtsAdapters;
import com.knowflow.interview.career.discover.adapters.FeedAdapters;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源注册表：政策与适配器集中登记，便于审计与逐源启停。
 *
 * <p>政策字段来自各源公开文档（2026-11-03 复核）。条款会变化，新增来源或调整频率前
 * 必须重新核对 `officialDocumentation` / `termsUrl`，不能沿用历史结论。</p>
 */
@Component
public class JobSourceRegistry {

    private static final String REVIEWED_AT = "2026-11-03";

    private final Map<String, JobSourceAdapter> adapters = new LinkedHashMap<>();

    public JobSourceRegistry(ObjectMapper json) {
        HttpJson http = new HttpJson(json);

        // Remotive：公开免鉴权；>2 请求/分钟会被封，官方建议 ≤4 次/天。
        register(new FeedAdapters.Remotive(http, json, new JobModels.JobSourcePolicy(
                "remotive", true,
                "https://remotive.com/api/remote-jobs",
                "https://remotive.com/terms",
                true, "数据来源：Remotive（remotive.com）", true,
                20, 30_000L, 3600L, false, false, REVIEWED_AT,
                "公开流约 20 条且延迟约 24 小时；必须回链署名，不得转供给其他招聘站")));

        // Remote OK：数组首元素为法律声明；要求可跟随回链并署名。
        register(new FeedAdapters.RemoteOk(http, json, new JobModels.JobSourcePolicy(
                "remoteok", true,
                "https://remoteok.com/api",
                "https://remoteok.com/terms",
                true, "数据来源：Remote OK（remoteok.com）", true,
                60, 10_000L, 1800L, false, false, REVIEWED_AT,
                "首元素不是岗位；无公开频率上限，按保守间隔请求")));

        // Jobicy：仅近 7 天，延迟约 3 小时，要求署名与跳转原站。
        register(new FeedAdapters.Jobicy(http, json, new JobModels.JobSourcePolicy(
                "jobicy", true,
                "https://jobicy.com/api/v2/remote-jobs",
                "https://jobicy.com/terms",
                true, "数据来源：Jobicy（jobicy.com）", true,
                60, 8_000L, 3600L, false, false, REVIEWED_AT,
                "仅提供近 7 天岗位，无法回溯历史")));

        // Himalayas：20 条/次，24 小时刷新，必须可见回链。
        register(new FeedAdapters.Himalayas(http, json, new JobModels.JobSourcePolicy(
                "himalayas", true,
                "https://himalayas.app/jobs/api",
                "https://himalayas.app/terms",
                true, "数据来源：Himalayas（himalayas.app）", true,
                20, 60_000L, 86_400L, false, false, REVIEWED_AT,
                "24 小时刷新，频繁轮询没有收益")));

        // Greenhouse：仅单公司维度，必须由用户提供 board token。
        register(new AtsAdapters.Greenhouse(http, json, new JobModels.JobSourcePolicy(
                "greenhouse", true,
                "https://developers.greenhouse.io/job-board.html",
                "https://www.greenhouse.io/terms",
                false, null, true,
                30, 3_000L, 900L, false, false, REVIEWED_AT,
                "不是全站搜索：必须输入公司 board token")));

        // Lever：同上，必须提供 site 名。
        register(new AtsAdapters.Lever(http, json, new JobModels.JobSourcePolicy(
                "lever", true,
                "https://github.com/lever/postings-api",
                "https://www.lever.co/terms",
                false, null, true,
                30, 3_000L, 900L, false, false, REVIEWED_AT,
                "不是全站搜索：必须输入公司 site 名")));
    }

    private void register(JobSourceAdapter adapter) {
        adapters.put(adapter.id(), adapter);
    }

    public List<JobSourceAdapter> all() {
        return List.copyOf(adapters.values());
    }

    public JobSourceAdapter get(String sourceId) {
        return adapters.get(sourceId);
    }

    /** 仅返回「政策启用 + 当前查询适用」的来源；未知来源忽略而不是报错。 */
    public List<JobSourceAdapter> enabledFor(JobModels.JobQuery query) {
        List<String> requested = query.sources();
        return adapters.values().stream()
                .filter(adapter -> adapter.policy().enabled())
                .filter(adapter -> requested.isEmpty() || requested.contains(adapter.id()))
                .filter(adapter -> adapter.supports(query))
                .toList();
    }

    /** 前端来源列表：只暴露真实注册且已启用的源。 */
    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (JobSourceAdapter adapter : adapters.values()) {
            JobModels.JobSourcePolicy policy = adapter.policy();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceId", policy.sourceId());
            item.put("displayName", adapter.displayName());
            item.put("enabled", policy.enabled());
            item.put("attributionRequired", policy.attributionRequired());
            item.put("attributionText", policy.attributionText());
            item.put("termsUrl", policy.termsUrl());
            item.put("documentation", policy.officialDocumentation());
            item.put("cacheTtlSeconds", policy.cacheTtlSeconds());
            item.put("lastPolicyReviewedAt", policy.lastPolicyReviewedAt());
            item.put("note", policy.note());
            list.add(item);
        }
        return list;
    }
}
