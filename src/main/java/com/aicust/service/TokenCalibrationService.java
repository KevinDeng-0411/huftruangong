package com.aicust.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenCalibrationService {

    private final RedisTemplate<String, Object> redisTemplate;

    public TokenCalibrationService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String key(String model) {
        return "ai:token:calibration:" + model;
    }

    // 获取校准因子
    public double getFactor(String model) {
        // 如果 Redis 里没数据，返回默认值 1.0
        try {
            Object est = redisTemplate.opsForHash().get(key(model), "est");
            Object act = redisTemplate.opsForHash().get(key(model), "act");

            if (est == null || act == null) return 1.0;

            double e = Double.parseDouble(est.toString());
            double a = Double.parseDouble(act.toString());

            if (e < 100) return 1.0; // 样本太少不校准

            return Math.min(Math.max(a / e, 0.8), 1.2); // 限制范围
        } catch (Exception e) {
            return 1.0; // 出错兜底
        }
    }

    // 记录反馈
    public void feedback(String model, int estimated, int actual) {
        redisTemplate.opsForHash().increment(key(model), "est", estimated);
        redisTemplate.opsForHash().increment(key(model), "act", actual);
    }
}
