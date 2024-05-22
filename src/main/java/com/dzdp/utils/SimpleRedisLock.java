package com.dzdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

// 分布式锁
public class SimpleRedisLock implements ILock {
    private final String name; // 锁的用户名称
    private StringRedisTemplate stringRedisTemplate;

    private static final String preFix = "lock:";
    private final String preFix_ID = UUID.randomUUID(true).toString();

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean TryLock(long timeSec) {
        String name1 = Thread.currentThread().getName() + preFix_ID;

        Boolean aBoolean = stringRedisTemplate.opsForValue()
                .setIfAbsent(preFix + name, name1, timeSec, TimeUnit.SECONDS);
        
        return Boolean.TRUE.equals(aBoolean); // 以免包装类爆空指针异常
    }

    @Override
    public void unlock() {
        String lockId = preFix_ID + Thread.currentThread().getName();
        if(lockId.equals(stringRedisTemplate.opsForValue().get(preFix + name))){
            stringRedisTemplate.delete(preFix + name);
        }

    }
}
