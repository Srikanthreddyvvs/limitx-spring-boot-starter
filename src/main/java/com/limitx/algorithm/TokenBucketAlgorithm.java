package com.limitx.algorithm;

import com.limitx.model.AlgorithmType;
import com.limitx.model.RateLimitDecision;
import com.limitx.model.RateLimitRule;
import com.limitx.redis.LuaScriptExecutor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;


public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final LuaScriptExecutor luaExecutor;

    public TokenBucketAlgorithm(LuaScriptExecutor luaExecutor) {
        this.luaExecutor = luaExecutor;
    }

    @Override
    public Mono<RateLimitDecision> isAllowed(String bucketKey, RateLimitRule rule) {
        long now = Instant.now().getEpochSecond();

        return luaExecutor.execute(
                LuaScriptExecutor.TOKEN_BUCKET,
                List.of(bucketKey),
                List.of(
                        String.valueOf(rule.getLimit()),
                        String.valueOf(rule.getRefillTokens()),
                        String.valueOf(rule.getWindowSeconds()),
                        String.valueOf(now)
                )
        ).map(results -> {
            long allowed        = results.get(0);
            long remaining      = results.get(1);
            long nextRefillAt   = results.get(2);
            long retryAfter     = nextRefillAt - now;

            if (allowed == 1L) {
                return RateLimitDecision.allow(rule.getLimit(), remaining, nextRefillAt, bucketKey);
            } else {
                return RateLimitDecision.deny(rule.getLimit(), nextRefillAt, retryAfter, bucketKey);
            }
        });
    }

    @Override
    public AlgorithmType type() {
        return AlgorithmType.TOKEN_BUCKET;
    }
}
