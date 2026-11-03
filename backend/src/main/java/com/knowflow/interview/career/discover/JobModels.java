package com.knowflow.interview.career.discover;

import com.knowflow.exception.BusinessException;

import java.util.List;

/**
 * 岗位发现的统一模型。
 *
 * <p>设计原则：上游没有提供的字段一律为 null，**不推测、不补造**。
 * 例如上游只给薪资文本时，salaryMin/Max 保持 null，由前端只展示文本。</p>
 */
public final class JobModels {

    private JobModels() { }

    /** 搜索条件：所有字段可空，空表示不限制。 */
    public record JobQuery(
            String keyword,
            String location,
            String company,
            Boolean remoteOnly,
            Integer salaryMinK,
            String experience,
            String education,
            String jobType,
            List<String> sources,
            int page,
            int size
    ) {
        public static final int MAX_KEYWORD = 100;

        public JobQuery {
            keyword = trim(keyword);
            location = trim(location);
            company = trim(company);
            experience = trim(experience);
            education = trim(education);
            jobType = trim(jobType);
            sources = sources == null ? List.of() : sources.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
            page = Math.max(1, Math.min(page, 20));
            size = Math.max(1, Math.min(size, 50));
        }

        public static JobQuery of(String keyword) {
            return new JobQuery(keyword, null, null, null, null, null, null, null, List.of(), 1, 20);
        }

        public String cacheKey() {
            return String.join("|",
                    n(keyword), n(location), n(company), remoteOnly == null ? "" : remoteOnly.toString(),
                    salaryMinK == null ? "" : salaryMinK.toString(), n(experience), n(education), n(jobType),
                    String.join(",", sources));
        }

        private static String n(String value) { return value == null ? "" : value; }

        private static String trim(String value) {
            if (value == null) return null;
            String trimmed = value.trim();
            if (trimmed.isEmpty()) return null;
            if (trimmed.length() > MAX_KEYWORD) throw new BusinessException(400, "搜索关键词最多100字");
            return trimmed;
        }

        /** 关键词匹配：上游的 search 参数不可靠，统一在服务端对标题/公司/技能做包含匹配。 */
        public boolean matches(String title, String company, List<String> tags) {
            if (keyword == null) return true;
            String needle = keyword.toLowerCase();
            if (title != null && title.toLowerCase().contains(needle)) return true;
            if (company != null && company.toLowerCase().contains(needle)) return true;
            return tags != null && tags.stream().anyMatch(t -> t != null && t.toLowerCase().contains(needle));
        }
    }

    /** 统一岗位模型；字段来自上游，缺失即 null。 */
    public record NormalizedJob(
            String sourceId,
            String sourceName,
            String externalId,
            String title,
            String company,
            String location,
            String salaryText,
            Integer salaryMin,
            Integer salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            String experience,
            String education,
            String jobType,
            String remoteType,
            String description,
            List<String> tags,
            List<String> requiredSkills,
            List<String> preferredSkills,
            String sourceUrl,
            String applicationUrl,
            String postedAt,
            String fetchedAt,
            String attribution,
            String freshness
    ) {
        public NormalizedJob {
            description = truncate(description, 20000);
            sourceUrl = truncate(sourceUrl, 2048);
            applicationUrl = truncate(applicationUrl, 2048);
            tags = tags == null ? List.of() : List.copyOf(tags);
            requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
            preferredSkills = preferredSkills == null ? List.of() : List.copyOf(preferredSkills);
        }

        private static String truncate(String value, int max) {
            if (value == null) return null;
            return value.length() <= max ? value : value.substring(0, max);
        }

        /** 去重主键：来源 + 外部 ID；缺失时回退到规范化 URL。 */
        public String dedupeKey() {
            if (externalId != null && !externalId.isBlank()) return "ext:" + sourceId + ":" + externalId;
            if (sourceUrl != null && !sourceUrl.isBlank()) return "url:" + normalizeUrl(sourceUrl);
            return "soft:" + sourceId + ":" + safe(company) + ":" + safe(title) + ":" + safe(location);
        }

        /** 疑似重复：同公司同标题同城市，仅用于提示，不直接删除。 */
        public String softKey() {
            return (safe(company) + "|" + safe(title) + "|" + safe(location)).toLowerCase();
        }

        private static String safe(String value) { return value == null ? "" : value.trim(); }

        private static String normalizeUrl(String url) {
            String value = url.trim().toLowerCase();
            int hash = value.indexOf('#');
            if (hash >= 0) value = value.substring(0, hash);
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }
    }

    /** 单个来源的执行状态，用于前端展示降级信息。 */
    public record SourceStatus(
            String sourceId,
            String displayName,
            String status,
            boolean stale,
            String message,
            int count
    ) { }

    /** 搜索响应：items + 每个来源的状态 + 是否有来源失败。 */
    public record SearchResult(
            List<NormalizedJob> items,
            List<SourceStatus> sources,
            boolean partial,
            List<String> attribution,
            int page,
            int size,
            int total
    ) { }

    /**
     * 数据源政策：频率、缓存、署名、可存储性必须逐个来源配置。
     *
     * <p>条款会变化，`lastPolicyReviewedAt` 用于提醒复核；未复核的来源不得默认启用。</p>
     */
    public record JobSourcePolicy(
            String sourceId,
            boolean enabled,
            String officialDocumentation,
            String termsUrl,
            boolean attributionRequired,
            String attributionText,
            boolean applicationLinkRequired,
            int maxRequestsPerWindow,
            long minimumRequestIntervalMs,
            long cacheTtlSeconds,
            boolean storageAllowed,
            boolean descriptionStorageAllowed,
            String lastPolicyReviewedAt,
            String note
    ) { }
}
