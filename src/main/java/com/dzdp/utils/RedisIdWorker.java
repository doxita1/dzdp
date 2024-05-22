package com.dzdp.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final Long BEGIN_TIMESTAMP = 1701628200L; //2023-12-03T18:30的秒数
    private static final Integer COUNT_BITS = 32;

    public Long createId(String prefix) {
        long l = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        // 生成时间戳
        long time = l - BEGIN_TIMESTAMP;


        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + date);

        return time << COUNT_BITS | count;
    }

}
