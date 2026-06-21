package com.aicust.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;


// 声明该类为 Spring 的配置类，相当于一个 XML 配置文件，
// Spring 容器会扫描并处理其中带有 @Bean 注解的方法。
@Configuration
public class RedisConfig {

    // 将该方法的返回值注册为 Spring 容器中的一个 Bean（即交由 Spring 管理的对象），
    // Bean 的名称默认为方法名 "redisTemplate"，类型为 RedisTemplate<String, Object>。
    // 方法参数 RedisConnectionFactory 会由 Spring 自动注入（通常由 Spring Boot 自动配置提供）。
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {

        // 创建一个 RedisTemplate 实例，用于操作 Redis 数据库。
        // 泛型 <String, Object> 表示 key 类型为 String，value 类型为 Object（可存储任意 Java 对象）。
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // 设置 Redis 连接工厂，用于建立与 Redis 服务器的连接。
        // factory 由 Spring 自动注入，包含 Redis 的连接信息（如主机、端口、密码等）。
        template.setConnectionFactory(factory);

        // 设置 key 的序列化方式为 StringRedisSerializer，
        // 表示 Redis 中的 key 将以普通字符串形式存储（可读性强，推荐用于 String 类型 key）。
        template.setKeySerializer(new StringRedisSerializer());

        // 设置 value 的序列化方式为 GenericJackson2JsonRedisSerializer，
        // 表示存入 Redis 的 Java 对象会被序列化为 JSON 字符串（使用 Jackson 库），
        // 读取时也能自动反序列化回原对象。支持泛型和复杂对象。
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        // 返回配置好的 RedisTemplate 实例，供其他组件注入使用。
        return template;
    }
    @Bean
    public DefaultRedisScript<Long> tokenFreezeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/token_freeze.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
