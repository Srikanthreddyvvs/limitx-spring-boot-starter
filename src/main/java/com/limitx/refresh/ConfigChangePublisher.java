package com.limitx.refresh;

import com.limitx.redis.RedisKeyBuilder;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * Publishes a message on the Redis Pub/Sub config-changes channel whenever
 * a rule config key is written or deleted via {@code RedisRuleRepository}.
 * <p>
 * The message payload is simply the affected Redis config key string
 * (e.g. {@code "rl:config:user:123"}). Every gateway instance subscribed
 * to the channel receives this and evicts exactly that key from its local
 * Caffeine L1 cache — no full cache flush, no restart, no polling.
 * <p>
 * <b>Multi-instance behaviour:</b> Redis Pub/Sub delivers the message to
 * <i>all</i> current subscribers on the channel. If you have three gateway
 * pods running, all three receive the event and all three evict their
 * stale local copy. The next request on each pod re-reads from Redis and
 * re-populates L1 with the fresh rule.
 */
public class ConfigChangePublisher {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String channel;

    public ConfigChangePublisher(ReactiveStringRedisTemplate redisTemplate,
                                 RedisKeyBuilder keyBuilder) {
        this.redisTemplate = redisTemplate;
        this.channel       = keyBuilder.configChangesChannel();
    }

    /**
     * Publishes the changed config key to the Pub/Sub channel.
     *
     * @param configKey the Redis key that was written or deleted
     *                  (e.g. {@code "rl:config:endpoint:/api/orders"})
     * @return a {@link Mono<Void>} that completes when the publish is done
     */
    public Mono<Void> publish(String configKey) {
        return redisTemplate.convertAndSend(channel, configKey).then();
    }
}
