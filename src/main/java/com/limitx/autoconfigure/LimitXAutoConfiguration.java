package com.limitx.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limitx.algorithm.*;
import com.limitx.cache.RuleCache;
import com.limitx.filter.LimitXGatewayFilterFactory;
import com.limitx.filter.RequestContextExtractor;
import com.limitx.metrics.RateLimitMetrics;
import com.limitx.redis.LuaScriptExecutor;
import com.limitx.redis.RedisKeyBuilder;
import com.limitx.redis.RedisRuleRepository;
import com.limitx.refresh.ConfigChangePublisher;
import com.limitx.refresh.ConfigChangeSubscriber;
import com.limitx.resolver.RuleResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.util.List;

/**
 * Wires all LimitX beans in dependency order.
 * Activated automatically when this starter is on the classpath.
 * Disable entirely with: limitx.enabled=false
 *
 * Bean wiring order (each depends on the ones above it):
 *   1. RedisKeyBuilder        — key patterns, no deps
 *   2. LuaScriptExecutor      — loads Lua scripts, needs RedisTemplate
 *   3. Algorithm beans        — Fixed/Sliding/Token, need LuaScriptExecutor
 *   4. AlgorithmFactory       — needs all 3 algorithm beans
 *   5. RuleCache              — Caffeine L1, no Redis dep
 *   6. ConfigChangePublisher  — needs RedisTemplate + KeyBuilder
 *   7. ConfigChangeSubscriber — needs ConnectionFactory + RuleCache + KeyBuilder
 *   8. RedisRuleRepository    — needs RedisTemplate + KeyBuilder + Publisher + ObjectMapper
 *   9. RuleResolver           — needs RuleCache + Repository + KeyBuilder + Properties
 *  10. RequestContextExtractor — stateless, no deps
 *  11. RateLimitMetrics        — needs MeterRegistry
 *  12. LimitXFilter            — needs everything above
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "limitx", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LimitXProperties.class)
public class LimitXAutoConfiguration {

    // 1. Key builder
    @Bean
    @ConditionalOnMissingBean
    public RedisKeyBuilder redisKeyBuilder(LimitXProperties props) {
        return new RedisKeyBuilder(props.getKeyPrefix());
    }

    // 2. Lua script executor
    @Bean
    @ConditionalOnMissingBean
    public LuaScriptExecutor luaScriptExecutor(ReactiveStringRedisTemplate redisTemplate) {
        return new LuaScriptExecutor(redisTemplate);
    }

    // 3. Algorithm implementations
    @Bean
    public FixedWindowAlgorithm fixedWindowAlgorithm(LuaScriptExecutor executor) {
        return new FixedWindowAlgorithm(executor);
    }

    @Bean
    public SlidingWindowAlgorithm slidingWindowAlgorithm(LuaScriptExecutor executor) {
        return new SlidingWindowAlgorithm(executor);
    }

    @Bean
    public TokenBucketAlgorithm tokenBucketAlgorithm(LuaScriptExecutor executor) {
        return new TokenBucketAlgorithm(executor);
    }

    // 4. Algorithm factory
    @Bean
    @ConditionalOnMissingBean
    public AlgorithmFactory algorithmFactory(List<RateLimitAlgorithm> algorithms) {
        return new AlgorithmFactory(algorithms);
    }

    // 5. L1 cache
    @Bean
    @ConditionalOnMissingBean
    public RuleCache ruleCache(LimitXProperties props) {
        return new RuleCache(
                props.getCache().getTtlSeconds(),
                props.getCache().getMaxSize()
        );
    }

    // 6. Pub/Sub publisher
    @Bean
    @ConditionalOnMissingBean
    public ConfigChangePublisher configChangePublisher(ReactiveStringRedisTemplate redisTemplate,
                                                       RedisKeyBuilder keyBuilder) {
        return new ConfigChangePublisher(redisTemplate, keyBuilder);
    }

    // 7. Pub/Sub subscriber
    @Bean
    @ConditionalOnMissingBean
    public ConfigChangeSubscriber configChangeSubscriber(ReactiveRedisConnectionFactory connectionFactory,
                                                         RuleCache ruleCache,
                                                         RedisKeyBuilder keyBuilder,
                                                         LimitXProperties props) {
        return new ConfigChangeSubscriber(
                connectionFactory, ruleCache, keyBuilder,
                props.getRefresh().isEnabled()
        );
    }

    // 8. Redis rule repository
    @Bean
    @ConditionalOnMissingBean
    public RedisRuleRepository redisRuleRepository(ReactiveStringRedisTemplate redisTemplate,
                                                   RedisKeyBuilder keyBuilder,
                                                   ConfigChangePublisher changePublisher,
                                                   ObjectMapper objectMapper) {
        return new RedisRuleRepository(redisTemplate, keyBuilder, changePublisher, objectMapper);
    }

    // 9. Rule resolver
    @Bean
    @ConditionalOnMissingBean
    public RuleResolver ruleResolver(RuleCache ruleCache,
                                     RedisRuleRepository ruleRepository,
                                     RedisKeyBuilder keyBuilder,
                                     LimitXProperties props) {
        return new RuleResolver(ruleCache, ruleRepository, keyBuilder, props);
    }

    // 10. Request context extractor
    @Bean
    @ConditionalOnMissingBean
    public RequestContextExtractor requestContextExtractor() {
        return new RequestContextExtractor();
    }

    // 11. Metrics
    @Bean
    @ConditionalOnMissingBean
    public RateLimitMetrics rateLimitMetrics(MeterRegistry meterRegistry) {
        return new RateLimitMetrics(meterRegistry);
    }

    // 12. Gateway filter factory — the entry point developers add to their routes
    @Bean
    @ConditionalOnMissingBean
    public LimitXGatewayFilterFactory limitXGatewayFilterFactory(RuleResolver ruleResolver,
                                     AlgorithmFactory algorithmFactory,
                                     RequestContextExtractor contextExtractor,
                                     RedisKeyBuilder keyBuilder,
                                     RateLimitMetrics metrics) {
        return new LimitXGatewayFilterFactory(ruleResolver, algorithmFactory, contextExtractor, keyBuilder, metrics);
    }
}
