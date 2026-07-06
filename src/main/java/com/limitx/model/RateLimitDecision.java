package com.limitx.model;

/**
 * The outcome of running a RateLimitRule against the current request,
 * as produced by an algorithm implementation (RateLimitAlgorithm) and consumed by
 * LimitXFilter to either let the request through or return 429 Too Many Requests}.
 * <p>
 * The fields here map directly onto the standard rate-limit response headers:
 * <pre>
 *   X-RateLimit-Limit:     limit
 *   X-RateLimit-Remaining: remaining
 *   X-RateLimit-Reset:     resetEpochSeconds
 *   Retry-After:           retryAfterSeconds   (only set when !allowed)
 * </pre>
 */
public final class RateLimitDecision {

    private final boolean allowed;
    private final long limit;
    private final long remaining;
    private final long resetEpochSeconds;
    private final long retryAfterSeconds;
    private final String matchedRuleKey;

    private RateLimitDecision(boolean allowed, long limit, long remaining,
                               long resetEpochSeconds, long retryAfterSeconds,
                               String matchedRuleKey) {
        this.allowed = allowed;
        this.limit = limit;
        this.remaining = remaining;
        this.resetEpochSeconds = resetEpochSeconds;
        this.retryAfterSeconds = retryAfterSeconds;
        this.matchedRuleKey = matchedRuleKey;
    }

    /**
     * Creates an "allowed" decision: the request is let through.
     *
     * @param limit             the limit from the matched rule
     * @param remaining         requests/tokens remaining after this one is counted
     * @param resetEpochSeconds epoch second at which the window resets / bucket fully refills
     * @param matchedRuleKey    the Redis config key of the rule that was applied (for debugging/headers)
     */
    public static RateLimitDecision allow(long limit, long remaining, long resetEpochSeconds, String matchedRuleKey) {
        return new RateLimitDecision(true, limit, remaining, resetEpochSeconds, 0, matchedRuleKey);
    }

    /**
     * Creates a "denied" decision: the caller should receive 429 with
     * a Retry-After header.
     */
    public static RateLimitDecision deny(long limit, long resetEpochSeconds, long retryAfterSeconds, String matchedRuleKey) {
        return new RateLimitDecision(false, limit, 0, resetEpochSeconds, retryAfterSeconds, matchedRuleKey);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getLimit() {
        return limit;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getResetEpochSeconds() {
        return resetEpochSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** Which Redis config key (rule) produced this decision, e.g. rl:config:endpoint:/api/orders. */
    public String getMatchedRuleKey() {
        return matchedRuleKey;
    }

    @Override
    public String toString() {
        return "RateLimitDecision{" +
                "allowed=" + allowed +
                ", limit=" + limit +
                ", remaining=" + remaining +
                ", resetEpochSeconds=" + resetEpochSeconds +
                ", retryAfterSeconds=" + retryAfterSeconds +
                ", matchedRuleKey='" + matchedRuleKey + '\'' +
                '}';
    }
}
