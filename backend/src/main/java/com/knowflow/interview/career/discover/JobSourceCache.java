package com.knowflow.interview.career.discover;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 来源级缓存与限频。
 *
 * <p>缓存只放内存：公开源的条款普遍不允许把整份 JD 持久化到第三方库，
 * 进程重启即丢失，避免把「缓存时间」当成「发布时间」。同一来源的所有用户共享缓存，
 * 不会因为 N 个用户搜索就打 N 倍请求。</p>
 */
@Component
public class JobSourceCache {

    public record Entry(List<JobModels.NormalizedJob> items, Instant fetchedAt) { }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFetch = new ConcurrentHashMap<>();

    public Optional<Entry> find(String sourceId, String cacheKey) {
        return Optional.ofNullable(cache.get(key(sourceId, cacheKey)));
    }

    public boolean isFresh(Optional<Entry> entry, JobModels.JobSourcePolicy policy) {
        return entry.isPresent()
                && entry.get().fetchedAt().plusSeconds(policy.cacheTtlSeconds()).isAfter(Instant.now());
    }

    /** 是否已超过来源的最小请求间隔；未请求过视为可以请求。 */
    public boolean canFetch(String sourceId, JobModels.JobSourcePolicy policy) {
        Instant last = lastFetch.get(sourceId);
        return last == null || last.plusMillis(policy.minimumRequestIntervalMs()).isBefore(Instant.now());
    }

    public void put(String sourceId, String cacheKey, List<JobModels.NormalizedJob> items) {
        cache.put(key(sourceId, cacheKey), new Entry(List.copyOf(items), Instant.now()));
    }

    public void markFetched(String sourceId) {
        lastFetch.put(sourceId, Instant.now());
    }

    public boolean hasAny(String sourceId, String cacheKey) {
        return cache.containsKey(key(sourceId, cacheKey));
    }

    /** 仅供测试与运维观测。 */
    public int size() {
        return cache.size();
    }

    private String key(String sourceId, String cacheKey) {
        return sourceId + "::" + cacheKey;
    }
}
