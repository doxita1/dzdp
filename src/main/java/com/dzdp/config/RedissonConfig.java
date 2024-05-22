package com.dzdp.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// redisson的配置类
@Configuration
@Slf4j
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        return Redisson.create(config);
    }
    
    @Bean
    public <T> RBloomFilter<T> initializeBloomFilter() {
        RBloomFilter<T> bloomFilter = redissonClient().getBloomFilter("shopId");
        // 尝试初始化布隆过滤器，预计插入100000个元素，误判率为0.01
        boolean initialized = bloomFilter.tryInit(100000L, 0.01);
        if (initialized) {
            log.info("Bloom filter initialized successfully.");
        } else {
            log.info("Bloom filter already initialized.");
        }
        return bloomFilter;
    }
}
