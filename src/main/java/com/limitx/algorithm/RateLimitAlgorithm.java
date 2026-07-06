package com.limitx.algorithm;

import com.limitx.model.RateLimitDecision;
import com.limitx.model.RateLimitRule;
import reactor.core.publisher.Mono;


public interface RateLimitAlgorithm {

    Mono<RateLimitDecision> isAllowed(String bucketKey, RateLimitRule rule);

    com.limitx.model.AlgorithmType type();
}
