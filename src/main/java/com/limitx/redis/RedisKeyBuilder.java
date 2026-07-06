package com.limitx.redis;

import com.limitx.model.RateLimitScope;
import com.limitx.model.RequestContext;


public class RedisKeyBuilder {

    private final String prefix;

    public RedisKeyBuilder(String prefix) {
        this.prefix = prefix;
    }

    // Config keys — where RateLimitRule JSON is stored

    public String configKey(RateLimitScope scope, RequestContext ctx) {
        return switch (scope) {
            case GLOBAL   -> prefix + ":config:global";
            case USER     -> prefix + ":config:user:"     + ctx.getUserId();
            case IP       -> prefix + ":config:ip:"       + ctx.getIp();
            case ENDPOINT -> prefix + ":config:endpoint:" + ctx.getPath();
        };
    }


    // Bucket keys — where runtime counter / token state is stored


    public String bucketKey(RateLimitScope scope, RequestContext ctx) {
        return switch (scope) {
            case GLOBAL   -> prefix + ":bucket:global";
            case USER     -> prefix + ":bucket:user:"     + ctx.getUserId();
            case IP       -> prefix + ":bucket:ip:"       + ctx.getIp();
            case ENDPOINT -> prefix + ":bucket:endpoint:" + ctx.getPath();
        };
    }


    // Pub/Sub channel — published to when a config key changes


    public String configChangesChannel() {
        return prefix + ":config-changes";
    }
}
