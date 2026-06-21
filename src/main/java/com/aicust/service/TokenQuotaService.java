package com.aicust.service;

import com.aicust.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class TokenQuotaService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> tokenFreezeScript;
    private final UserRepository userRepository; // 1. 注入 UserRepository

    // 2. 更新构造函数
    public TokenQuotaService(StringRedisTemplate redisTemplate,
                             DefaultRedisScript<Long> tokenFreezeScript,
                             UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.tokenFreezeScript = tokenFreezeScript;
        this.userRepository = userRepository;
    }

    /**
     * 1. 检查并预冻结配额 (原子操作)
     */
    public void check(Long userId, int limit, int estimated) {
        String usedKey = "ai:quota:used:" + userId;
        String frozenKey = "ai:quota:frozen:" + userId;

        Long result = redisTemplate.execute(
                tokenFreezeScript,
                List.of(usedKey, frozenKey),
                String.valueOf(limit),
                String.valueOf(estimated)
        );

        if (result != null && result == -1) {
            throw new RuntimeException("配额不足 (今日限额: " + limit + ")");
        }
    }

    /**
     * 2. 结算实际用量 (成功调用后)
     * 修改点：增加异步写入 MySQL 的逻辑
     */
    public void settle(Long userId, int estimated, int actual) {
        String usedKey = "ai:quota:used:" + userId;
        String frozenKey = "ai:quota:frozen:" + userId;

        // Redis 操作 (保持不变)
        // 1. 释放当初冻结的额度
        redisTemplate.opsForValue().decrement(frozenKey, estimated);
        // 2. 增加实际使用的额度
        redisTemplate.opsForValue().increment(usedKey, actual);

        // 🆕 MySQL 异步更新 (新增)
        // 使用 CompletableFuture.runAsync 将数据库 IO 操作放入后台线程池执行
        CompletableFuture.runAsync(() -> {
            try {
                // 调用我们在 Repository 中定义的原子扣减方法
                userRepository.decreaseBalance(userId, actual);
            } catch (Exception e) {
                // 在生产环境中，这里应该记录日志 (log.error)
                // 如果对数据一致性要求极高，可以考虑发送到死信队列或重试机制
                System.err.println("【异步同步失败】用户 " + userId + " 消耗 " + actual + " Token 同步 MySQL 失败: " + e.getMessage());
            }
        });
    }

    /**
     * 3. 回滚冻结 (调用失败后)
     */
    public void rollback(Long userId, int estimated) {
        String frozenKey = "ai:quota:frozen:" + userId;
        redisTemplate.opsForValue().decrement(frozenKey, estimated);
    }

    /**
     * 查询今日已用额度
     */
    public int getUsedToken(Long userId) {
        String usedKey = "ai:quota:used:" + userId;
        String val = redisTemplate.opsForValue().get(usedKey);
        return val == null ? 0 : Integer.parseInt(val);
    }
}

