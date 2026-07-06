package com.limitx.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limitx.model.RateLimitRule;
import com.limitx.model.RateLimitScope;
import com.limitx.model.RequestContext;
import com.limitx.refresh.ConfigChangePublisher;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * Reads and writes {@link RateLimitRule} objects in Redis as JSON strings
 * under {@code rl:config:*} keys.
 * <p>
 * This is the only class that should ever write config keys — doing so
 * automatically publishes a change event on the Pub/Sub channel so that
 * every running gateway instance invalidates its L1 Caffeine cache entry
 * for that key (see {@code ConfigChangeSubscriber}).
 * <p>
 * <b>Jackson note:</b> {@link RateLimitRule} has a no-args constructor and
 * public setters, so the default {@link ObjectMapper} can deserialize it
 * without any custom configuration.
 */
public class RedisRuleRepository {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final ConfigChangePublisher changePublisher;
    private final ObjectMapper objectMapper;

    public RedisRuleRepository(ReactiveStringRedisTemplate redisTemplate,
                               RedisKeyBuilder keyBuilder,
                               ConfigChangePublisher changePublisher,
                               ObjectMapper objectMapper) {
        this.redisTemplate    = redisTemplate;
        this.keyBuilder       = keyBuilder;
        this.changePublisher  = changePublisher;
        this.objectMapper     = objectMapper;
    }

    /**
     * Finds the rule stored under the config key for the given scope + context.
     * Returns {@link Mono#empty()} if no rule is configured for that key —
     * the {@code RuleResolver} treats empty as "no match, try next scope."
     */
    public Mono<RateLimitRule> findRule(RateLimitScope scope, RequestContext ctx) {
        String configKey = keyBuilder.configKey(scope, ctx);
        return redisTemplate.opsForValue()
                .get(configKey)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, RateLimitRule.class));
                    } catch (JsonProcessingException e) {
                        // Malformed JSON in Redis — log and treat as no rule found
                        return Mono.error(new IllegalStateException(
                                "Failed to parse rule at key [" + configKey + "]: " + e.getMessage(), e));
                    }
                });
    }

    /**
     * Saves a rule to Redis as JSON and publishes a config-change event so
     * all gateway instances drop their cached copy of this key from L1.
     * <p>
     * This is how operators update limits at runtime with no restart:
     * <pre>
     *   redis-cli SET rl:config:endpoint:/api/orders '{"scope":"ENDPOINT","algorithm":"TOKEN_BUCKET","limit":500,"windowSeconds":60,"refillTokens":500}'
     * </pre>
     * Or programmatically via this method (e.g. from an admin API).
     */
    public Mono<Void> saveRule(RateLimitScope scope, RequestContext ctx, RateLimitRule rule) {
        String configKey = keyBuilder.configKey(scope, ctx);
        try {
            String json = objectMapper.writeValueAsString(rule);
            return redisTemplate.opsForValue()
                    .set(configKey, json)
                    .then(changePublisher.publish(configKey)); // notify all instances
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Failed to serialize rule: " + e.getMessage(), e));
        }
    }

    /**
     * Deletes a rule from Redis and publishes a change event so L1 caches
     * are invalidated — the resolver will then fall through to the next scope.
     */
    public Mono<Void> deleteRule(RateLimitScope scope, RequestContext ctx) {
        String configKey = keyBuilder.configKey(scope, ctx);
        return redisTemplate.opsForValue()
                .delete(configKey)
                .then(changePublisher.publish(configKey));
    }
}
