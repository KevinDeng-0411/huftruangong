package com.aicust.service;

import org.springframework.stereotype.Service;

@Service
public class MultiLevelRateLimitService {

    private final LocalRateLimiter localRateLimiter;
    private final RateLimitService slidingWindowService;
    private final SentinelService sentinelService;
    private final RateLimitConfigService configService;

    public MultiLevelRateLimitService(LocalRateLimiter localRateLimiter,
                                      RateLimitService slidingWindowService,
                                      SentinelService sentinelService,
                                      RateLimitConfigService configService) {
        this.localRateLimiter = localRateLimiter;
        this.slidingWindowService = slidingWindowService;
        this.sentinelService = sentinelService;
        this.configService = configService;
    }

    public boolean allow(String key, String api) {
        int qps = configService.getQps(api);

        if (!localRateLimiter.tryAcquire(key, qps)) {
            return false;
        }

        if (!sentinelService.allow(api)) {
            return false;
        }

        return slidingWindowService.allow(key, 1, qps);
    }
}
