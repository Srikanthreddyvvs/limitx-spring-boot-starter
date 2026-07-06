package com.limitx.refresh;

import com.limitx.cache.RuleCache;
import com.limitx.redis.RedisKeyBuilder;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

/**
 * Subscribes to the Redis Pub/Sub config-changes channel and evicts the
 * corresponding Caffeine L1 cache entry whenever a rule is updated.
 * <p>
 * This is what makes LimitX's dynamic refresh work:
 * <ol>
 *   <li>Operator writes a new rule to Redis directly (e.g. via redis-cli or
 *       an admin API that calls {@code RedisRuleRepository.saveRule}).</li>
 *   <li>{@code ConfigChangePublisher} publishes the affected config key on
 *       {@code rl:config-changes}.</li>
 *   <li>This subscriber receives the message on every running gateway instance
 *       and calls {@code ruleCache.evict(configKey)}.</li>
 *   <li>The next request for that key misses L1, re-reads from Redis, and
 *       sees the updated rule — all without a restart.</li>
 * </ol>
 * <p>
 * <b>Implementation note:</b> we use Spring Data Redis's reactive
 * {@link ReactiveRedisMessageListenerContainer}, which keeps a single
 * dedicated connection open for subscriptions without blocking any thread.
 * The subscription is set up in the constructor and stays active for the
 * lifetime of the application context.
 */
public class ConfigChangeSubscriber {

    private final ReactiveRedisMessageListenerContainer listenerContainer;

    public ConfigChangeSubscriber(ReactiveRedisConnectionFactory connectionFactory,
                                  RuleCache ruleCache,
                                  RedisKeyBuilder keyBuilder,
                                  boolean refreshEnabled) {
        this.listenerContainer = new ReactiveRedisMessageListenerContainer(connectionFactory);

        if (refreshEnabled) {
            String channel = keyBuilder.configChangesChannel();

            // Subscribe to the channel; for each message, evict the L1 cache entry
            // The message body is the affected config key string
            listenerContainer
                    .receive(ChannelTopic.of(channel))
                    .map(message -> message.getMessage()) // extract String payload
                    .doOnNext(configKey -> {
                        ruleCache.evict(configKey);
                        // configKey looks like: rl:config:user:123
                        // Caffeine evicts just that one entry — O(1), no lock
                    })
                    .onErrorContinue((err, msg) -> {
                        // Don't let a bad message crash the subscription loop
                        // In production you'd log this with a structured logger
                        System.err.println("[LimitX] Error processing config change event: " + err.getMessage());
                    })
                    .subscribe(); // keep subscription alive for the app's lifetime
        }
    }
}
