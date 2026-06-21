package com.aicust.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitConfigService {

    private final ConcurrentHashMap<String, Integer> config = new ConcurrentHashMap<>();

    public RateLimitConfigService() {
        config.put("/api/test", 5);
    }

    public int getQps(String api) {
        return config.getOrDefault(api, 10);
    }

    public void update(String api, int qps) {
        config.put(api, qps);
    }
}
