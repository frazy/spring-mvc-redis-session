package com.bitlei.sample.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import redis.clients.jedis.JedisPoolConfig;

/**
 * @author hzyinglei
 *
 */
@Configuration
public class RedisConfiguration {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.redis") // 建议和databaseRedisProperties分开
    public RedisProperties businessRedisProperties() {
        return new RedisProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.redis")
    public RedisProperties databaseRedisProperties() {
        return new RedisProperties();
    }

    @Bean
    public RedisTemplate<String, Object> sessionRedisTemplate(@Qualifier("databaseRedisProperties") RedisProperties properties) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<String, Object>();
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setConnectionFactory(buildJedisConnectionFactory(properties));
        return redisTemplate;
    }

    public static JedisConnectionFactory buildJedisConnectionFactory(RedisProperties properties) {
        JedisConnectionFactory connectionFactory = new JedisConnectionFactory();
        connectionFactory.setDatabase(properties.getDatabase());
        connectionFactory.setHostName(properties.getHost());
        connectionFactory.setPassword(properties.getPassword());
        connectionFactory.setPort(properties.getPort());
        connectionFactory.setTimeout(properties.getTimeout());

        connectionFactory.setUsePool(false);
        if (properties.getPool() != null) {
            connectionFactory.setUsePool(true);
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(properties.getPool().getMaxActive());
            poolConfig.setMaxIdle(properties.getPool().getMaxIdle());
            poolConfig.setMinIdle(properties.getPool().getMinIdle());
            poolConfig.setMaxWaitMillis(properties.getPool().getMaxWait());
            connectionFactory.setPoolConfig(poolConfig);
        }
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

}
