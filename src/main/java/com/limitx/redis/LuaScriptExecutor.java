package com.limitx.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.List;


@SuppressWarnings("unchecked")
public class LuaScriptExecutor {

    // Script name constants — used by algorithm classes to identify which script to run
    public static final String FIXED_WINDOW   = "fixed_window";
    public static final String SLIDING_WINDOW = "sliding_window";
    public static final String TOKEN_BUCKET   = "token_bucket";

    private final ReactiveStringRedisTemplate redisTemplate;

    private final RedisScript<List> fixedWindowScript;
    private final RedisScript<List> slidingWindowScript;
    private final RedisScript<List> tokenBucketScript;

    public LuaScriptExecutor(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        // Load scripts from src/main/resources/scripts/ at bean construction time.
        // RedisScript.of(resource, returnType) reads and stores the script body;
        // Spring sends it to Redis on first EVAL, then uses EVALSHA for all subsequent calls.
        this.fixedWindowScript   = RedisScript.of(
                new ClassPathResource("scripts/fixed_window.lua"),   List.class);
        this.slidingWindowScript = RedisScript.of(
                new ClassPathResource("scripts/sliding_window.lua"), List.class);
        this.tokenBucketScript   = RedisScript.of(
                new ClassPathResource("scripts/token_bucket.lua"),   List.class);
    }

    public Mono<List<Long>> execute(String scriptName, List<String> keys, List<String> args) {
        RedisScript<List> script = switch (scriptName) {
            case FIXED_WINDOW   -> fixedWindowScript;
            case SLIDING_WINDOW -> slidingWindowScript;
            case TOKEN_BUCKET   -> tokenBucketScript;
            default -> throw new IllegalArgumentException("Unknown script: " + scriptName);
        };

        return redisTemplate.execute(script, keys, args)
                .next() // script returns one multi-bulk reply → take it
                .cast((Class<List<Long>>) (Class<?>) List.class);
    }
}
