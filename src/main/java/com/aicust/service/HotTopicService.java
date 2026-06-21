package com.aicust.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import java.util.Set;

@Service
public class HotTopicService {

    private final StringRedisTemplate redisTemplate;
    private static final String HOT_KEY = "ai:stats:hottopics";

    public HotTopicService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 异步统计热点，不阻塞主线程
     */
    @Async("taskExecutor") // 需要在启动类或配置类开启 @EnableAsync
    public void track(String prompt) {
        // 简单策略：截取前 10 个字作为 Topic (实际生产应接入 NLP 分词服务)
        String topic = prompt.length() > 10 ? prompt.substring(0, 10) : prompt;

        // ZINCRBY: 话题热度 +1
        redisTemplate.opsForZSet().incrementScore(HOT_KEY, topic, 1);
    }

    /**
     * 获取前 N 名热搜
     */
    public Set<String> getTopTopics(int n) {
        // ZREVRANGE: 倒序取前 N 个
        return redisTemplate.opsForZSet().reverseRange(HOT_KEY, 0, n - 1);
    }
}