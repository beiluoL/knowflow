package com.knowflow.interview.career.discover.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.interview.career.discover.HttpJson;
import com.knowflow.interview.career.discover.JobModels;
import com.knowflow.interview.career.discover.JobSourceAdapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 公开招聘板适配器（Remotive / Remote OK / Jobicy / Himalayas）。
 *
 * <p>四家都提供免鉴权 JSON 流，但字段、分页与署名要求不同；这里只做字段映射。
 * 上游没有的字段（学历、年限、精确薪资）保持 null——不根据描述猜。</p>
 */
public final class FeedAdapters {

    private FeedAdapters() { }

    private static String now() {
        return Instant.now().toString();
    }

    static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && value.isValueNode()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) return text.trim();
            }
        }
        return null;
    }

    static Integer intOrNull(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) continue;
            if (value.isNumber()) return value.asInt();
            if (value.isTextual()) {
                String digits = value.asText().replaceAll("[^0-9]", "");
                if (!digits.isEmpty() && digits.length() <= 9) return Integer.parseInt(digits);
            }
        }
        return null;
    }

    static List<String> tags(JsonNode node, String... fields) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) continue;
            if (value.isArray()) {
                value.forEach(item -> {
                    if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText().trim());
                });
            } else if (value.isTextual()) {
                for (String part : value.asText().split("[,，;；]")) {
                    if (!part.isBlank()) values.add(part.trim());
                }
            }
        }
        return new ArrayList<>(values).subList(0, Math.min(values.size(), 20));
    }

    /** 把 HTML 片段转成纯文本，保留换行；只用于展示，不做富文本渲染。 */
    static String plain(String html) {
        if (html == null) return null;
        String text = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    static List<JsonNode> arrayOf(JsonNode root, String... fields) {
        List<JsonNode> items = new ArrayList<>();
        if (root == null) return items;
        if (root.isArray()) {
            root.forEach(items::add);
            return items;
        }
        for (String field : fields) {
            JsonNode value = root.get(field);
            if (value != null && value.isArray()) {
                value.forEach(items::add);
                return items;
            }
        }
        return items;
    }

    /** Remotive：https://remotive.com/api/remote-jobs（Terms: 2 请求/分钟，需回链署名）。 */
    public static class Remotive implements JobSourceAdapter {
        private final HttpJson http;
        private final ObjectMapper json;
        private final JobModels.JobSourcePolicy policy;

        public Remotive(HttpJson http, ObjectMapper json, JobModels.JobSourcePolicy policy) {
            this.http = http;
            this.json = json;
            this.policy = policy;
        }

        public String id() { return "remotive"; }
        public String displayName() { return "Remotive"; }
        public JobModels.JobSourcePolicy policy() { return policy; }

        public List<JobModels.NormalizedJob> search(JobModels.JobQuery query) {
            JsonNode root = http.get("https://remotive.com/api/remote-jobs?limit=50", "KnowFlowCareer/3.0 (+personal job search)");
            List<JobModels.NormalizedJob> jobs = new ArrayList<>();
            for (JsonNode node : arrayOf(root, "jobs")) {
                String title = text(node, "title");
                String company = text(node, "company_name");
                List<String> tags = tags(node, "tags");
                if (!query.matches(title, company, tags)) continue;
                jobs.add(new JobModels.NormalizedJob(
                        id(), displayName(), text(node, "id"), title, company,
                        text(node, "candidate_required_location"), text(node, "salary"),
                        null, null, null, null, null, null,
                        text(node, "job_type"), "REMOTE",
                        plain(text(node, "description")), tags, List.of(), List.of(),
                        text(node, "url"), null,
                        text(node, "publication_date"), now(),
                        policy.attributionText(), "LIVE"));
            }
            return jobs;
        }
    }

    /** Remote OK：https://remoteok.com/api（数组第 0 个元素是法律声明，必须跳过）。 */
    public static class RemoteOk implements JobSourceAdapter {
        private final HttpJson http;
        private final ObjectMapper json;
        private final JobModels.JobSourcePolicy policy;

        public RemoteOk(HttpJson http, ObjectMapper json, JobModels.JobSourcePolicy policy) {
            this.http = http;
            this.json = json;
            this.policy = policy;
        }

        public String id() { return "remoteok"; }
        public String displayName() { return "Remote OK"; }
        public JobModels.JobSourcePolicy policy() { return policy; }

        public List<JobModels.NormalizedJob> search(JobModels.JobQuery query) {
            JsonNode root = http.get("https://remoteok.com/api", "KnowFlowCareer/3.0 (+personal job search)");
            List<JobModels.NormalizedJob> jobs = new ArrayList<>();
            for (JsonNode node : arrayOf(root)) {
                // 首元素只有 legal/last_updated，没有岗位字段
                if (node.has("legal") || !node.has("id") || !node.has("position")) continue;
                String title = text(node, "position");
                String company = text(node, "company");
                List<String> tags = tags(node, "tags");
                if (!query.matches(title, company, tags)) continue;
                Integer min = intOrNull(node, "salary_min");
                Integer max = intOrNull(node, "salary_max");
                String salaryText = min != null && max != null ? min + " - " + max + " USD/year" : null;
                jobs.add(new JobModels.NormalizedJob(
                        id(), displayName(), text(node, "id"), title, company,
                        text(node, "location"), salaryText, min, max, min != null ? "USD" : null,
                        min != null ? "YEAR" : null, null, null,
                        null, "REMOTE", plain(text(node, "description")), tags, List.of(), List.of(),
                        text(node, "url"), text(node, "apply_url"),
                        text(node, "date", "epoch"), now(),
                        policy.attributionText(), "LIVE"));
            }
            return jobs;
        }
    }

    /** Jobicy：https://jobicy.com/api/v2/remote-jobs（仅近 7 天）。 */
    public static class Jobicy implements JobSourceAdapter {
        private final HttpJson http;
        private final ObjectMapper json;
        private final JobModels.JobSourcePolicy policy;

        public Jobicy(HttpJson http, ObjectMapper json, JobModels.JobSourcePolicy policy) {
            this.http = http;
            this.json = json;
            this.policy = policy;
        }

        public String id() { return "jobicy"; }
        public String displayName() { return "Jobicy"; }
        public JobModels.JobSourcePolicy policy() { return policy; }

        public List<JobModels.NormalizedJob> search(JobModels.JobQuery query) {
            JsonNode root = http.get("https://jobicy.com/api/v2/remote-jobs?count=50", "KnowFlowCareer/3.0 (+personal job search)");
            List<JobModels.NormalizedJob> jobs = new ArrayList<>();
            for (JsonNode node : arrayOf(root, "jobs")) {
                String title = text(node, "jobTitle");
                String company = text(node, "companyName");
                List<String> tags = tags(node, "jobIndustry", "jobLevel");
                if (!query.matches(title, company, tags)) continue;
                Integer min = intOrNull(node, "annualSalaryMin");
                Integer max = intOrNull(node, "annualSalaryMax");
                String salaryText = min != null && max != null
                        ? min + " - " + max + " " + valueOr(text(node, "salaryCurrency"), "") + "/year" : null;
                jobs.add(new JobModels.NormalizedJob(
                        id(), displayName(), text(node, "id"), title, company,
                        text(node, "jobGeo"), salaryText, min, max, text(node, "salaryCurrency"),
                        min != null ? "YEAR" : null, text(node, "jobLevel"), null,
                        text(node, "jobType"), "REMOTE",
                        plain(text(node, "jobDescription", "jobExcerpt")), tags, List.of(), List.of(),
                        text(node, "url"), null, text(node, "pubDate"), now(),
                        policy.attributionText(), "LIVE"));
            }
            return jobs;
        }

        private static String valueOr(String value, String fallback) {
            return value == null ? fallback : value;
        }
    }

    /** Himalayas：https://himalayas.app/jobs/api（20 条/次，24 小时刷新）。 */
    public static class Himalayas implements JobSourceAdapter {
        private final HttpJson http;
        private final ObjectMapper json;
        private final JobModels.JobSourcePolicy policy;

        public Himalayas(HttpJson http, ObjectMapper json, JobModels.JobSourcePolicy policy) {
            this.http = http;
            this.json = json;
            this.policy = policy;
        }

        public String id() { return "himalayas"; }
        public String displayName() { return "Himalayas"; }
        public JobModels.JobSourcePolicy policy() { return policy; }

        public List<JobModels.NormalizedJob> search(JobModels.JobQuery query) {
            JsonNode root = http.get("https://himalayas.app/jobs/api?limit=20", "KnowFlowCareer/3.0 (+personal job search)");
            List<JobModels.NormalizedJob> jobs = new ArrayList<>();
            for (JsonNode node : arrayOf(root, "jobs", "data")) {
                String title = text(node, "title", "jobTitle");
                String company = text(node, "companyName", "company");
                List<String> tags = tags(node, "categories", "tags", "skills");
                if (!query.matches(title, company, tags)) continue;
                Integer min = intOrNull(node, "minSalary", "salaryMin");
                Integer max = intOrNull(node, "maxSalary", "salaryMax");
                String salaryText = min != null || max != null
                        ? (min == null ? "" : min) + " - " + (max == null ? "" : max) + " " +
                          (text(node, "salaryCurrency") == null ? "" : text(node, "salaryCurrency")) : null;
                String location = text(node, "locationRestrictions", "location");
                jobs.add(new JobModels.NormalizedJob(
                        id(), displayName(),
                        text(node, "guid", "id"), title, company, location,
                        salaryText == null ? null : salaryText.trim(), min, max,
                        text(node, "salaryCurrency"), min != null ? "YEAR" : null,
                        text(node, "seniority"), null, text(node, "employmentType"), "REMOTE",
                        plain(text(node, "description", "excerpt")), tags, List.of(), List.of(),
                        text(node, "applicationLink", "url"), null,
                        text(node, "pubDate", "createdAt"), now(),
                        policy.attributionText(), "LIVE"));
            }
            return jobs;
        }
    }
}
