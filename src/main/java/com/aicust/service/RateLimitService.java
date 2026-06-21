package com.aicust.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private static final long WINDOW_MILLIS = 1000L;
    private static final long EXPIRE_SECONDS = 2L;

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean allow(String key, int requestCost, int qps) {
        String redisKey = "limit:sw:" + key;
        long now = System.currentTimeMillis();
        long minScore = now - WINDOW_MILLIS;

        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, minScore);

        Long current = redisTemplate.opsForZSet().zCard(redisKey);
        long currentCount = current == null ? 0L : current;
        if (currentCount + requestCost > qps) {
            return false;
        }

        for (int i = 0; i < requestCost; i++) {
            String member = now + "-" + i;
            redisTemplate.opsForZSet().add(redisKey, member, now);
        }
        redisTemplate.expire(redisKey, EXPIRE_SECONDS, TimeUnit.SECONDS);
        return true;
    }
}
