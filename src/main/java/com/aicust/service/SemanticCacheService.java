package com.aicust.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Service
public class SemanticCacheService {

    private final StringRedisTemplate redisTemplate;

    public SemanticCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取缓存答案
     * @param prompt 用户问题
     * @return 缓存的 AI 回复，无缓存则返回 null
     */
    public String getCachedAnswer(String prompt) {
        // 1. 计算 Prompt 的指纹 (MD5)
        // 进阶：此处可调用 EmbeddingModel 获得向量，然后在 Redis Vector Store 中搜相似度 > 0.9 的 Key
        String hash = md5(prompt);
        String key = "ai:cache:q:" + hash;

        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 写入缓存 (设置 1 小时过期，避免时效性问题)
     */
    public void cacheAnswer(String prompt, String answer) {
        if (answer == null || answer.length() < 5) return; // 太短的不存

        String hash = md5(prompt);
        String key = "ai:cache:q:" + hash;
        redisTemplate.opsForValue().set(key, answer, Duration.ofHours(1));
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
