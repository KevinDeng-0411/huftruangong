package com.aicust.service;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalRateLimiter {

    private final ConcurrentHashMap<String, RateLimiter> cache = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, double qps) {
        RateLimiter limiter = cache.compute(key, (k, existing) -> {
            if (existing == null || Double.compare(existing.getRate(), qps) != 0) {
                return RateLimiter.create(qps);
            }
            return existing;
        });
        return limiter != null && limiter.tryAcquire();
    }
}
