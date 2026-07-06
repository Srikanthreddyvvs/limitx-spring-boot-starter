package com.limitx.model;

import java.util.Objects;

/**
 * A single rate limiting rule, as stored under a rl:config:* key in
 * Redis (serialized to/from JSON) and resolved by the RuleResolver.
 * Example JSON for a token bucket rule on /api/orders:
 * <pre>{@code
 * {
 *   "scope": "ENDPOINT",
 *   "algorithm": "TOKEN_BUCKET",
 *   "limit": 100,
 *   "windowSeconds": 60,
 *   "refillTokens": 100
 * }
 * }</pre>
 * Updating this JSON directly in Redis (under the matching {@code rl:config:*}
 * key) and publishing on the refresh channel is how operators change limits
 * at runtime without restarting the gateway.
 */
public class RateLimitRule {

    private RateLimitScope scope;
    private AlgorithmType algorithm;

    private long limit;

    private long windowSeconds;

    private long refillTokens;

    public RateLimitRule() {
    }

    private RateLimitRule(Builder builder) {
        this.scope = builder.scope;
        this.algorithm = builder.algorithm;
        this.limit = builder.limit;
        this.windowSeconds = builder.windowSeconds;
        this.refillTokens = builder.refillTokens;
    }

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

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RateLimitRule)) return false;
        RateLimitRule that = (RateLimitRule) o;
        return limit == that.limit
                && windowSeconds == that.windowSeconds
                && refillTokens == that.refillTokens
                && scope == that.scope
                && algorithm == that.algorithm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, algorithm, limit, windowSeconds, refillTokens);
    }

    @Override
    public String toString() {
        return "RateLimitRule{" +
                "scope=" + scope +
                ", algorithm=" + algorithm +
                ", limit=" + limit +
                ", windowSeconds=" + windowSeconds +
                ", refillTokens=" + refillTokens +
                '}';
    }

    /**
     * Builder for {@link RateLimitRule}, used both in tests and when seeding
     * default rules programmatically.
     */
    public static final class Builder {
        private RateLimitScope scope;
        private AlgorithmType algorithm;
        private long limit;
        private long windowSeconds;
        private long refillTokens;

        private Builder() {
        }

        public Builder scope(RateLimitScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder algorithm(AlgorithmType algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder limit(long limit) {
            this.limit = limit;
            return this;
        }

        public Builder windowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
            return this;
        }

        public Builder refillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
            return this;
        }

        public RateLimitRule build() {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(algorithm, "algorithm must not be null");
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be > 0");
            }
            if (windowSeconds <= 0) {
                throw new IllegalArgumentException("windowSeconds must be > 0");
            }
            return new RateLimitRule(this);
        }
    }
}
