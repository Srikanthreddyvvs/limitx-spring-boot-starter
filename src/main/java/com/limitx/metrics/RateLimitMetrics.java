package com.limitx.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;


public class RateLimitMetrics {

    private static final String ALLOWED_METRIC = "limitx.requests.allowed";
    private static final String DENIED_METRIC  = "limitx.requests.denied";

    private final MeterRegistry meterRegistry;

    public RateLimitMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Increments the allowed counter for the matched rule key.
     * Called by LimitXFilter when a request passes rate limiting.
     */
    public void recordAllowed(String ruleKey) {
        Counter.builder(ALLOWED_METRIC)
                .description("Number of requests allowed by LimitX")
                .tag("rule", ruleKey)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increments the denied counter for the matched rule key.
     * Called by LimitXFilter when a request is rejected (429).
     */
    public void recordDenied(String ruleKey) {
        Counter.builder(DENIED_METRIC)
                .description("Number of requests denied by LimitX")
                .tag("rule", ruleKey)
                .register(meterRegistry)
                .increment();
    }
}
