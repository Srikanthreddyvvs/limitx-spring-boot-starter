package com.limitx.resolver;

import com.limitx.autoconfigure.LimitXProperties;
import com.limitx.cache.RuleCache;
import com.limitx.model.AlgorithmType;
import com.limitx.model.RateLimitRule;
import com.limitx.model.RateLimitScope;
import com.limitx.model.RequestContext;
import com.limitx.redis.RedisKeyBuilder;
import com.limitx.redis.RedisRuleRepository;
import reactor.core.publisher.Mono;

/**
 * Resolves which {@link RateLimitRule} applies to an incoming request by
 * walking the four scopes in priority order:
 * <pre>
 *   1. USER     (most specific — per-account business tier)
 *   2. IP       (anonymous / pre-auth protection)
 *   3. ENDPOINT (protect a specific route from aggregate traffic)
 *   4. GLOBAL   (catch-all fallback)
 * </pre>
 * For each scope it:
 * <ol>
 *   <li>Builds the Redis config key via {@link RedisKeyBuilder}.</li>
 *   <li>Checks the Caffeine L1 cache ({@link RuleCache}) — O(1) local lookup.</li>
 *   <li>On L1 miss, reads from Redis ({@link RedisRuleRepository}) and
 *       populates L1 for subsequent requests.</li>
 *   <li>If Redis also has no entry for that key, moves to the next scope.</li>
 * </ol>
 * If no scope matches anything in Redis, falls back to the
 * {@code limitx.default-rule} from {@link LimitXProperties} — the gateway
 * is never left fully unprotected.
 * <p>
 * <b>Why enum ordinal for priority?</b> {@link RateLimitScope#values()} returns
 * values in declaration order, which is exactly the priority we want. The
 * resolver just iterates {@code values()} — no separate priority map needed,
 * the ordering is encoded structurally in the enum itself.
 */
public class RuleResolver {

    private final RuleCache ruleCache;
    private final RedisRuleRepository ruleRepository;
    private final RedisKeyBuilder keyBuilder;
    private final RateLimitRule defaultRule;

    public RuleResolver(RuleCache ruleCache,
                        RedisRuleRepository ruleRepository,
                        RedisKeyBuilder keyBuilder,
                        LimitXProperties properties) {
        this.ruleCache      = ruleCache;
        this.ruleRepository = ruleRepository;
        this.keyBuilder     = keyBuilder;
        this.defaultRule    = buildDefaultRule(properties);
    }

    /**
     * Returns the highest-priority rule that exists in Redis (or L1 cache)
     * for this request, or the configured default rule as a final fallback.
     * <p>
     * The returned {@link Mono} never completes empty — there is always at
     * least the default rule, so the filter can always make a decision.
     */
    public Mono<RateLimitRule> resolve(RequestContext ctx) {
        // Chain scope checks: USER → IP → ENDPOINT → GLOBAL
        // switchIfEmpty means "if this scope produced nothing, try the next one"
        return tryScope(RateLimitScope.USER, ctx)
                .switchIfEmpty(tryScope(RateLimitScope.IP, ctx))
                .switchIfEmpty(tryScope(RateLimitScope.ENDPOINT, ctx))
                .switchIfEmpty(tryScope(RateLimitScope.GLOBAL, ctx))
                .defaultIfEmpty(defaultRule); // properties fallback, never empty
    }

    /**
     * Attempts to find a rule for one scope:
     * L1 hit  → return immediately (no Redis call)
     * L1 miss → read Redis → if found, populate L1 and return
     *         → if not found in Redis → return empty (caller tries next scope)
     */
    private Mono<RateLimitRule> tryScope(RateLimitScope scope, RequestContext ctx) {
        // Skip USER scope when no userId is present (anonymous request)
        if (scope == RateLimitScope.USER && ctx.getUserId() == null) {
            return Mono.empty();
        }

        String configKey = keyBuilder.configKey(scope, ctx);

        // L1 check first
        return ruleCache.get(configKey)
                .map(Mono::just)
                .orElseGet(() ->
                        // L1 miss — go to Redis
                        ruleRepository.findRule(scope, ctx)
                                .doOnNext(rule -> ruleCache.put(configKey, rule)) // populate L1
                );
        // If Redis also has no entry, findRule returns Mono.empty()
        // → this whole method returns Mono.empty()
        // → caller's switchIfEmpty fires and tries the next scope
    }

    /**
     * Builds the in-memory default rule from {@code limitx.default-rule.*}
     * properties. This rule is used only when Redis has no config at any scope
     * — it keeps the gateway protected on a fresh deployment before any rules
     * have been seeded.
     */
    private RateLimitRule buildDefaultRule(LimitXProperties props) {
        LimitXProperties.DefaultRule p = props.getDefaultRule();
        return RateLimitRule.builder()
                .scope(p.getScope())
                .algorithm(p.getAlgorithm())
                .limit(p.getLimit())
                .windowSeconds(p.getWindowSeconds())
                .refillTokens(p.getRefillTokens())
                .build();
    }
}
