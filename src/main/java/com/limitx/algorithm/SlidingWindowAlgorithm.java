package com.limitx.algorithm;

import com.limitx.model.AlgorithmType;
import com.limitx.model.RateLimitDecision;
import com.limitx.model.RateLimitRule;
import com.limitx.redis.LuaScriptExecutor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;


public class SlidingWindowAlgorithm implements RateLimitAlgorithm {

    private final LuaScriptExecutor luaExecutor;

    public SlidingWindowAlgorithm(LuaScriptExecutor luaExecutor) {
        this.luaExecutor = luaExecutor;
    }

    @Override
    public Mono<RateLimitDecision> isAllowed(String bucketKey, RateLimitRule rule) {
        long now        = Instant.now().getEpochSecond();
        long windowSlot = now / rule.getWindowSeconds();
        long resetAt    = (windowSlot + 1) * rule.getWindowSeconds();

        String currKey = bucketKey + ":curr";
        String prevKey = bucketKey + ":prev";

        return luaExecutor.execute(
                LuaScriptExecutor.SLIDING_WINDOW,
                List.of(currKey, prevKey),
                List.of(
                        String.valueOf(rule.getLimit()),
                        String.valueOf(rule.getWindowSeconds()),
                        String.valueOf(now)
                )
        ).map(results -> {
            long allowed    = results.get(0);
            long remaining  = results.get(1);
            long retryAfter = resetAt - now;

            if (allowed == 1L) {
                return RateLimitDecision.allow(rule.getLimit(), remaining, resetAt, bucketKey);
            } else {
                return RateLimitDecision.deny(rule.getLimit(), resetAt, retryAfter, bucketKey);
            }
        });
    }

    @Override
    public AlgorithmType type() {
        return AlgorithmType.SLIDING_WINDOW;
    }
}
