package com.knowflow.interview.career.discover.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.interview.career.discover.HttpJson;
import com.knowflow.interview.career.discover.JobModels;
import com.knowflow.interview.career.discover.JobSourceAdapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业官方招聘板适配器（Greenhouse / Lever）。
 *
 * <p>这两家是「单公司」维度的公开接口：不存在跨公司搜索，必须先知道公司的
 * board token / site 名。因此 {@link #supports} 只在用户输入公司标识时返回 true，
 * 避免把它们伪装成全站搜索源。</p>
 */
public final class AtsAdapters {

    private AtsAdapters() { }

    private static String now() { return Instant.now().toString(); }

    /** Greenhouse 职位看板接口：https://developers.greenhouse.io/job-board.html */
    public static class Greenhouse implements JobSourceAdapter {
        private final HttpJson http;
        private final ObjectMapper json;
        private final JobModels.JobSourcePolicy policy;

        public Greenhouse(HttpJson http, ObjectMapper json, JobModels.JobSourcePolicy policy) {
            this.http = http;
            this.json = json;
            this.policy = policy;
        }

        public String id() { return "greenhouse"; }
        public String displayName() { return "Greenhouse 企业板"; }
        public JobModels.JobSourcePolicy policy() { return policy; }

        @Override
        public boolean supports(JobModels.JobQuery query) {
            return query.company() != null && query.company().matches("[A-Za-z0-9_-]{1,64}");
        }

        public List<JobModels.NormalizedJob> search(JobModels.JobQuery query) {
            if (!supports(query)) return List.of();
            String token = query.company();
            JsonNode root = http.get("https://boards-api.greenhouse.io/v1/boards/" + token + "/jobs?content=true",
                    "KnowFlowCareer/3.0 (+personal job search)");
            List<JobModels.NormalizedJob> jobs = new ArrayList<>();
            for (JsonNode node : FeedAdapters.arrayOf(root, "jobs")) {
                String title = FeedAdapters.text(node, "title");
                String location = node.path("location").path("name").asText(null);
                List<String> tags = List.of();
                if (!query.matches(title, token, tags)) continue;
                jobs.add(new JobModels.NormalizedJob(
                        id(), displayName(), FeedAdapters.text(node, "id"), title, token,
                        location, null, null, null, null, null, null, null,
                        null, null,
                        FeedAdapters.plain(FeedAdapters.text(node, "content")),
                        tags, List.of(), List.of(),
                        FeedAdapters.text(node, "absolute_url"), null,
                        FeedAdapters.text(node, "updated_at"), now(),
                        policy.attributionText(), "LIVE"));
            }
            return jobs;
        }
    }

    /** Lever 职位发布接口：https://github.com/lever/postings-api */
    public static class Lever implements JobSourceAdapter {
        private final HttpJson http;
        private final ObjectMapper json;
        private final JobModels.JobSourcePolicy policy;

        public Lever(HttpJson http, ObjectMapper json, JobModels.JobSourcePolicy policy) {
            this.http = http;
            this.json = json;
            this.policy = policy;
        }

        public String id() { return "lever"; }
        public String displayName() { return "Lever 企业板"; }
        public JobModels.JobSourcePolicy policy() { return policy; }

        @Override
        public boolean supports(JobModels.JobQuery query) {
            return query.company() != null && query.company().matches("[A-Za-z0-9_-]{1,64}");
        }

        public List<JobModels.NormalizedJob> search(JobModels.JobQuery query) {
            if (!supports(query)) return List.of();
            String site = query.company();
            JsonNode root = http.get("https://api.lever.co/v0/postings/" + site + "?mode=json",
                    "KnowFlowCareer/3.0 (+personal job search)");
            List<JobModels.NormalizedJob> jobs = new ArrayList<>();
            for (JsonNode node : FeedAdapters.arrayOf(root)) {
                String title = FeedAdapters.text(node, "text");
                String location = node.path("categories").path("location").asText(null);
                String commitment = node.path("categories").path("commitment").asText(null);
                if (!query.matches(title, site, List.of())) continue;
                jobs.add(new JobModels.NormalizedJob(
                        id(), displayName(), FeedAdapters.text(node, "id"), title, site,
                        location, null, null, null, null, null, null, null,
                        commitment, null,
                        FeedAdapters.plain(FeedAdapters.text(node, "descriptionPlain", "description")),
                        List.of(), List.of(), List.of(),
                        FeedAdapters.text(node, "hostedUrl"), FeedAdapters.text(node, "applyUrl"),
                        FeedAdapters.text(node, "createdAt"), now(),
                        policy.attributionText(), "LIVE"));
            }
            return jobs;
        }
    }
}
