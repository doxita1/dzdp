package com.dzdp;

import cn.hutool.json.JSONUtil;
import com.dzdp.entity.Shop;
import com.dzdp.mapper.ShopMapper;
import com.dzdp.service.IShopService;
import com.dzdp.utils.RedisConstants;
import com.dzdp.utils.RedisData;
import com.dzdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.dzdp.utils.RedisConstants.SHOP_GEO_KEY;

@Slf4j
@SpringBootTest
class dzDianPingApplicationTests {
    
    @Resource
    private IShopService shopService;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private RedisIdWorker idWorker;
    
    @Resource
    ShopMapper shopMapper;
    
    @Resource
    private RedissonClient redissonClient;
    private RLock lock;
    
    @BeforeEach
    void setLock() {
        lock = redissonClient.getLock("anyLock");
    }
    
    private ExecutorService es = Executors.newFixedThreadPool(500);
    
    
    @Test
    public void saveRedisData() {
        RedisData redisData = shopService.saveRedisData(1L, 20L);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + 1L, JSONUtil.toJsonStr(redisData));
    }
    
    @Test
    public void tryRedissonLock() throws InterruptedException {
        RLock name = redissonClient.getLock("name");
        boolean success = name.tryLock(1, 100, TimeUnit.SECONDS);
        try {
            if (success) {
                System.out.println("获取锁成功");
            } else {
                System.out.println("fail get lock!!!!!");
            }
        } finally {
//            name.unlock();
        }
    }
    
    
    @Test
    public void testId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                Long order = idWorker.createId("order");
                System.out.println("id=" + order);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time=" + (end - begin));

//        idWorker.createId("shopId");
    }
    
    @Test
    public void TestRedisClient() throws InterruptedException {
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败1");
            return;
        }
        log.info("获取锁1");
        try {
            method2();
        } finally {
            Thread.sleep(10000);
            log.info("释放锁1");
            lock.unlock();
        }
    }
    
    void method2() throws InterruptedException {
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败2");
            return;
        }
        try {
            
            log.info("获取锁2");
        } finally {
            log.info("释放锁2");
            Thread.sleep(20000);
            lock.unlock();
        }
    }
    
    @Test
    public void saveLocation() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> collect = list
                .stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : collect.entrySet()) {
            String key = SHOP_GEO_KEY + entry.getKey();
            List<Shop> shops = entry.getValue();
            // 使用Stream API将List<Shop>转换为List<RedisGeoCommands.GeoLocation<String>>
            List<RedisGeoCommands.GeoLocation<String>> locations = shops.stream()
                    .map(shop -> new RedisGeoCommands.GeoLocation<>(
                            shop.getId().toString(), // 使用商店的名称作为GeoLocation的名称
                            new Point(shop.getX(), shop.getY()) // 创建Point对象，需要传入经度和纬度
                    ))
                    .collect(Collectors.toList());
            
            // 添加转换后的地理位置信息到Redis
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
        
    }
    
    @Test
    public void initBloom(){
        RBloomFilter<Object> shopId = redissonClient.getBloomFilter("shopId");
        List<Long> longs = shopMapper.selectAllIds();
        for (Long aLong : longs) {
            boolean success = shopId.add(aLong);
            if(success){
                log.info("添加成功");
            }else{
                log.error("添加失败");
            }
        }
    }
}
