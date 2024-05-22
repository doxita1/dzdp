package com.dzdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dzdp.utils.RedisConstants.*;

@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private RedissonClient redissonClient;
    
    
    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 将任意java对象序列化为json并且存储在string类型的key中, 并且可以设置ttl过期时间
    public void setData(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    // 设置逻辑过期时间. 用以处理缓存击穿问题
    public void setLogicData(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 根据指定的key查询缓存, 并反序列化为指定类型, 利用缓存空值的方式解决缓存穿透问题
    // 解决缓存穿透
    public  <R,ID> R queryWithPassThrough(String preFix, ID id, Class<R> type , Function<ID,R> function,
                                          Long time, TimeUnit timeUnit){
        // TODO 查看缓存中有没有商品
        String key = preFix + id;

        String json = stringRedisTemplate.opsForValue().get(key);
        // TODO 有就直接返回
        if (StrUtil.isNotBlank(json)) {
            // 返回的是对象
            return JSONUtil.toBean(json,type);
        }
        
        RBloomFilter<ID> shopId = redissonClient.getBloomFilter("shopId");
        // 如果返回是空字符串, 报错(采用缓存空对象的方式解决缓存穿透)
        if (!shopId.contains(id)) {
            return null;
        }
        
        // TODO 没有就查数据库,利用函数式接口查询
        R value = function.apply(id);
        // TODO 没有报错
        if (value == null) {
            setData(key,"",CACHE_NULL_TTL,timeUnit);
            return null;
        }
        // TODO 有则返回写入缓存, 设置缓存过期时间, 方便更新缓存
        setData(key,JSONUtil.toJsonStr(value),CACHE_SHOP_TTL,timeUnit);
        return value;
    }

    // 根据指定的key查询缓存, 并反序列化为指定类型,
    // 利用逻辑过期的方式解决缓存击穿问题
    public  <R,ID> R queryWithLogic(String prefix ,ID id ,Class<R> type, Function<ID,R> function,
                                    Long time, TimeUnit timeUnit) {
        // TODO 查看缓存是否命中
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 没有命中就直接返回空
//        if (StrUtil.isBlank(json)) {
//            // 返回的是对象
//            return null;
//        }
        if (StrUtil.isBlank(json)) {
            // 查询数据库
            R value = function.apply(id);
            if (value == null) {
                // 数据库中没有，缓存空值防止缓存穿透
                setLogicData(key, "", CACHE_NULL_TTL, timeUnit);
                return null;
            }
            // 数据库中有，写入缓存并设置逻辑过期时间
            setLogicData(key, value, time, timeUnit);
            return value;
        }
        // 命中了判断是否过期
        // TODO 拿到对象

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 没有过期直接返回
            return r;
        }
        // 已经过期, 要缓存重建
        // 获得互斥锁
        boolean lock = getLock(id);
        // 获得互斥锁后跟着判断缓存是否已经完成更新
        if (lock && StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(key))) {
            // 拿到锁就开辟一个新线程来做缓存重建, 自己本身返回原来的shop
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    // 查数据库
                    R r1 = function.apply(id);
                    // 写入缓存
                    setLogicData(key,r1,time,timeUnit);
                    Thread.sleep(200);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    delLock(id);
                }
            });
        }
        // 如果没有获得锁就直接返回旧数据
        return r;
    }

    private <ID> boolean getLock(ID id) {
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isGetLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isGetLock);
    }

    // 释放锁 没有必要有返回值
    private <ID> void delLock(ID id) {
        stringRedisTemplate.delete(LOCK_SHOP_KEY + id);
    }
    // 在原来对象的基础上添加逻辑过期时间字段, 用类组合的方式组合逻辑过期字段
//    public RedisData saveRedisData(Long id, Long expireTime) {
//        Shop shop = getById(id);
//        RedisData data = new RedisData();
//        data.setData(shop);
//        data.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
//        return data;
//    }

    // 解决缓存穿透
//    private Shop queryWithPassThrough(Long id) {
//        // TODO 查看缓存中有没有商品
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // TODO 有就直接返回
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 返回的是对象
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 如果返回是空字符串, 报错(采用缓存空对象的方式解决缓存穿透)
//        if (shopJson != null) {
//            return null;
//        }
//        // TODO 没有就查数据库
//        Shop shop = getById(id);
//        // TODO 没有报错
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // TODO 有则返回写入缓存, 设置缓存过期时间, 方便更新缓存
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

    // 逻辑过期处理缓存击穿问题
//    private Shop queryWithLogic(Long id) {
//        // TODO 查看缓存是否命中
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 没有命中就直接返回空
//        if (StrUtil.isBlank(shopJson)) {
//            // 返回的是对象
//            return null;
//        }
//        // 命中了判断是否过期
//        // TODO 拿到对象
//
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 没有过期直接返回
//            return shop;
//        }
//        // 已经过期, 要缓存重建
//
//        // 获得互斥锁
//        boolean lock = getLock(id);
//        // 获得互斥锁后跟着判断缓存是否已经完成更新
//        if (lock && StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(key))) {
//            // 拿到锁就开辟一个新线程来做缓存重建, 自己本身返回原来的shop
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    RedisData redisData1 = saveRedisData(id, 10L);
//                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData1));
//                    Thread.sleep(200);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    // 释放锁
//                    delLock(id);
//                }
//            });
//        }
//        // 如果没有获得锁就直接返回旧数据
//        return shop;
//    }

//    互斥锁解决缓存击穿
//    private Shop queryWithMutex(Long id) {
//        // TODO 查看缓存中有没有商品
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // TODO 有就直接返回
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 返回的是对象
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 如果返回是空字符串
//        if (shopJson != null) {
//            return null;
//        }
//        // 判断拿到互斥锁的状态
//        boolean lockKey = getLock(id);
//
//        // 判断是否获取锁
//        try {
//            if (!lockKey) {
//                Thread.sleep(20);
//                // 没有得到锁就休眠并且重试
//                return queryWithMutex(id);
//            } else if (lockKey && StrUtil.isBlank(stringRedisTemplate.opsForValue().get(key))) {
//                // TODO 没有就查数据库
//                Shop shop = getById(id);
//                // 模拟真实业务场景
//                Thread.sleep(200);
//                // TODO 没有报错
//                if (shop == null) {
//                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                    return null;
//                }
//                // TODO 有则返回写入缓存, 设置缓存过期时间, 方便更新缓存
//                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//                return shop;
//            }
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            delLock(id);
//        }
//        return null;
//    }
}
