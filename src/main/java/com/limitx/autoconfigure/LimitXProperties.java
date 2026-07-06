package com.limitx.autoconfigure;

import com.limitx.model.AlgorithmType;
import com.limitx.model.RateLimitScope;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Binds all {@code limitx.*} properties from {@code application.yml}.
 * <p>
 * Example:
 * <pre>{@code
 * limitx:
 *   enabled: true
 *   key-prefix: rl
 *   cache:
 *     ttl-seconds: 30
 *     max-size: 10000
 *   refresh:
 *     enabled: true
 *     channel: rl:config-changes
 *   default-rule:
 *     scope: GLOBAL
 *     algorithm: FIXED_WINDOW
 *     limit: 1000
 *     window-seconds: 60
 * }</pre>
 * The {@code default-rule} is used as a last resort when no rule is found in
 * Redis for any scope, so a freshly-deployed gateway never runs completely
 * unprotected.
 */
@ConfigurationProperties(prefix = "limitx")
public class LimitXProperties {

    /** Master switch. When {@code false}, {@code LimitXFilter} passes every request through unchanged. */
    private boolean enabled = true;

    /**
     * Prefix used for every Redis key LimitX touches, e.g. with the
     * default value {@code "rl"} a per-user token bucket lives under
     * {@code rl:bucket:user:123} and its config under {@code rl:config:user:123}.
     */
    private String keyPrefix = "rl";

    @NestedConfigurationProperty
    private Cache cache = new Cache();

    @NestedConfigurationProperty
    private Refresh refresh = new Refresh();

    @NestedConfigurationProperty
    private DefaultRule defaultRule = new DefaultRule();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Refresh getRefresh() {
        return refresh;
    }

    public void setRefresh(Refresh refresh) {
        this.refresh = refresh;
    }

    public DefaultRule getDefaultRule() {
        return defaultRule;
    }

    public void setDefaultRule(DefaultRule defaultRule) {
        this.defaultRule = defaultRule;
    }

    /** Settings for the Caffeine L1 cache that sits in front of Redis-stored rule configs. */
    public static class Cache {

        /** How long a resolved rule stays in the L1 cache before it's eligible for expiry-based reload. */
        private long ttlSeconds = 30;

        /** Max number of distinct rule keys held in the L1 cache at once (Caffeine maximumSize). */
        private long maxSize = 10_000;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }
    }

    /** Settings for the Redis Pub/Sub based live-refresh mechanism. */
    public static class Refresh {

        /** When {@code true}, subscribes to {@link #channel} and evicts the L1 cache on config changes. */
        private boolean enabled = true;

        /** Redis Pub/Sub channel used to broadcast rule changes across gateway instances. */
        private String channel = "rl:config-changes";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }
    }

    /**
     * The fallback rule applied when no GLOBAL/USER/IP/ENDPOINT/GROUP rule is
     * found in Redis for a request. Acts as a safety net.
     */
    public static class DefaultRule {

        private RateLimitScope scope = RateLimitScope.GLOBAL;
        private AlgorithmType algorithm = AlgorithmType.FIXED_WINDOW;
        private long limit = 1000;
        private long windowSeconds = 60;
        private long refillTokens = 0;

        public RateLimitScope getScope() {
            return scope;
        }

        public void setScope(RateLimitScope scope) {
            this.scope = scope;
        }

        public AlgorithmType getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(AlgorithmType algorithm) {
            this.algorithm = algorithm;
        }

        public long getLimit() {
            return limit;
        }

        public void setLimit(long limit) {
            this.limit = limit;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }
    }
}
