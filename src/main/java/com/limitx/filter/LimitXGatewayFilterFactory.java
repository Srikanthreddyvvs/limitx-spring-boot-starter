package com.limitx.filter;

import com.limitx.algorithm.AlgorithmFactory;
import com.limitx.metrics.RateLimitMetrics;
import com.limitx.model.RateLimitDecision;
import com.limitx.model.RateLimitRule;
import com.limitx.model.RequestContext;
import com.limitx.redis.RedisKeyBuilder;
import com.limitx.resolver.RuleResolver;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;


public class LimitXGatewayFilterFactory extends AbstractGatewayFilterFactory<LimitXGatewayFilterFactory.Config> {

    private final RuleResolver            ruleResolver;
    private final AlgorithmFactory        algorithmFactory;
    private final RequestContextExtractor contextExtractor;
    private final RedisKeyBuilder         keyBuilder;
    private final RateLimitMetrics        metrics;

    public LimitXGatewayFilterFactory(RuleResolver ruleResolver,
                        AlgorithmFactory algorithmFactory,
                        RequestContextExtractor contextExtractor,
                        RedisKeyBuilder keyBuilder,
                        RateLimitMetrics metrics) {
        super(Config.class);
        this.ruleResolver     = ruleResolver;
        this.algorithmFactory = algorithmFactory;
        this.contextExtractor = contextExtractor;
        this.keyBuilder       = keyBuilder;
        this.metrics          = metrics;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            // extract request attributes (userId, ip, path, method)
            RequestContext ctx = contextExtractor.extract(exchange);

            // resolve rule, pick algorithm, execute Lua script
            return ruleResolver.resolve(ctx)
                    .flatMap(rule -> executeRateLimit(ctx, rule, exchange))
                    .flatMap(decision -> {
                        if (decision.isAllowed()) {
                            // Step 5a: allowed — add quota headers and pass through
                            setAllowedHeaders(exchange, decision);
                            metrics.recordAllowed(decision.getMatchedRuleKey());
                            return chain.filter(exchange);
                        } else {
                            // denied — return 429 with JSON body
                            metrics.recordDenied(decision.getMatchedRuleKey());
                            return writeDeniedResponse(exchange, decision);
                        }
                    });
        };
    }


    /**
     * Resolves the bucket key for this request + rule, selects the right
     * algorithm, and runs the Lua script.
     */
    private Mono<RateLimitDecision> executeRateLimit(RequestContext ctx,
                                                      RateLimitRule rule,
                                                      ServerWebExchange exchange) {
        // Bucket key = where runtime counter state is stored in Redis
        // e.g. rl:bucket:user:123  or  rl:bucket:endpoint:/api/orders
        String bucketKey = keyBuilder.bucketKey(rule.getScope(), ctx);

        return algorithmFactory
                .get(rule.getAlgorithm())
                .isAllowed(bucketKey, rule);
    }

    /**
     * Adds standard rate limit headers to an allowed response.
     */
    private void setAllowedHeaders(ServerWebExchange exchange, RateLimitDecision decision) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set("X-RateLimit-Limit",     String.valueOf(decision.getLimit()));
        headers.set("X-RateLimit-Remaining", String.valueOf(decision.getRemaining()));
        headers.set("X-RateLimit-Reset",     String.valueOf(decision.getResetEpochSeconds()));
    }

    private Mono<Void> writeDeniedResponse(ServerWebExchange exchange, RateLimitDecision decision) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

        HttpHeaders headers = response.getHeaders();
        headers.set("X-RateLimit-Limit",  String.valueOf(decision.getLimit()));
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset",  String.valueOf(decision.getResetEpochSeconds()));
        headers.set("Retry-After",        String.valueOf(decision.getRetryAfterSeconds()));
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":429,\"error\":\"Too Many Requests\"," +
                "\"message\":\"Rate limit exceeded. Retry after %d seconds.\"," +
                "\"retryAfter\":%d}",
                decision.getRetryAfterSeconds(),
                decision.getRetryAfterSeconds()
        );

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * No per-route YAML config needed — all rule configuration lives in Redis.
     * This empty class satisfies {@link AbstractGatewayFilterFactory}'s
     * generic type requirement.
     */
    public static class Config {
        // intentionally empty
    }
}
