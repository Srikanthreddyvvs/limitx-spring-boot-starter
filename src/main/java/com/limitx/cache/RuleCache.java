package com.limitx.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.limitx.model.RateLimitRule;

import java.time.Duration;
import java.util.Optional;


public class RuleCache {

    // Key = Redis config key string (e.g. "rl:config:user:123")
    // Value = the deserialized RateLimitRule stored at that key
    private final Cache<String, RateLimitRule> cache;

    public RuleCache(long ttlSeconds, long maxSize) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .build();
    }

    /**
     * Returns the cached rule for the given Redis config key, or
     */
    public Optional<RateLimitRule> get(String configKey) {
        return Optional.ofNullable(cache.getIfPresent(configKey));
    }

    /**
     * Stores a rule in the L1 cache under its Redis config key.
     */
    public void put(String configKey, RateLimitRule rule) {
        cache.put(configKey, rule);
    }

    /**
     * Evicts a single key from the L1 cache.
     * Called by ConfigChangeSubscriber when a Pub/Sub change event
     * arrives for this key — forces the next request to re-read from Redis.
     */
    public void evict(String configKey) {
        cache.invalidate(configKey);
    }

    /** Returns current number of entries held in the cache (for metrics/debugging). */
    public long size() {
        return cache.estimatedSize();
    }
}
