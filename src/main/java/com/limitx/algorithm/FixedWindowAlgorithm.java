package com.limitx.algorithm;

import com.limitx.model.AlgorithmType;
import com.limitx.model.RateLimitDecision;
import com.limitx.model.RateLimitRule;
import com.limitx.redis.LuaScriptExecutor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;


public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private final LuaScriptExecutor luaExecutor;

    public FixedWindowAlgorithm(LuaScriptExecutor luaExecutor) {
        this.luaExecutor = luaExecutor;
    }


    @Override
    public Mono<RateLimitDecision> isAllowed(String bucketKey, RateLimitRule rule) {
        long now = Instant.now().getEpochSecond();
        long windowSlot = now / rule.getWindowSeconds();
        // Append slot to key so each window period gets its own counter
        String windowKey = bucketKey + ":" + windowSlot;
        long resetAt = (windowSlot + 1) * rule.getWindowSeconds();

        return luaExecutor.execute(
                LuaScriptExecutor.FIXED_WINDOW,
                List.of(windowKey),
                List.of(
                        String.valueOf(rule.getLimit()),
                        String.valueOf(rule.getWindowSeconds()),
                        String.valueOf(now)
                )
        ).map(results -> {
            long allowed   = results.get(0);
            long remaining = results.get(1);
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
        return AlgorithmType.FIXED_WINDOW;
    }
}
