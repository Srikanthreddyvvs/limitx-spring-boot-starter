package com.limitx.algorithm;

import com.limitx.model.AlgorithmType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class AlgorithmFactory {

    private final Map<AlgorithmType, RateLimitAlgorithm> registry = new EnumMap<>(AlgorithmType.class);

    public AlgorithmFactory(List<RateLimitAlgorithm> algorithms) {
        for (RateLimitAlgorithm algorithm : algorithms) {
            registry.put(algorithm.type(), algorithm);
        }
    }

    public RateLimitAlgorithm get(AlgorithmType type) {
        RateLimitAlgorithm algorithm = registry.get(type);
        if (algorithm == null) {
            throw new IllegalArgumentException(
                    "No RateLimitAlgorithm implementation registered for type: " + type);
        }
        return algorithm;
    }
}
