package com.dzdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzdp.service.dto.Result;
import com.dzdp.entity.Shop;
import com.dzdp.mapper.ShopMapper;
import com.dzdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzdp.utils.CacheClient;
import com.dzdp.utils.RedisData;
import com.dzdp.utils.SystemConstants;
import org.redisson.api.RBloomFilter;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dzdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    
    @Resource
    private RBloomFilter<Long> bloomFilter;
    
    
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);
//        Shop shop = queryWithMutex(id);
//        Shop shop = queryWithLogic(id);
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class,
                this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        if(shop != null){
//            return Result.ok(shop);
//        }
        // 利用逻辑过期时间解决缓存击穿
//        Shop shop1 = cacheClient.queryWithLogic(CACHE_SHOP_KEY, id, Shop.class,
//                this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        return Result.ok(shop);
    }
    
    // 更新操作
    @Override
    @Transactional // 控制原子性
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺不存在");
        }
        // TODO 更新数据库
        updateById(shop);
        bloomFilter.add(shop.getId());
        // TODO 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
    
    
    // 获得互斥锁
    private boolean getLock(Long id) {
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isGetLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isGetLock);
    }
    
    // 释放锁 没有必要有返回值
    private void delLock(Long id) {
        Boolean delete = stringRedisTemplate.delete(LOCK_SHOP_KEY + id);
    }
    
    // 在原来对象的基础上添加逻辑过期时间字段, 用类组合的方式组合逻辑过期字段
    @Override
    public RedisData saveRedisData(Long id, Long expireTime) {
        Shop shop = getById(id);
        RedisData data = new RedisData();
        data.setData(shop);
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        return data;
    }
    
    @Override
    public Result querShopByType(Integer type, Integer current, Double x, Double y) {
        // 不需要距离判断, 直接分页查询
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", type)
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = from + SystemConstants.MAX_PAGE_SIZE;
        
        String key = SHOP_GEO_KEY + type;

////        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo().search(key,
////                GeoReference.fromCoordinate(x, y),
////                new Distance(5000),
////                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().limit(end).includeDistance());
////
////
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo().radius(key,
                new Circle(new Point(x, y), new Distance(5000, Metrics.METERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end));

////
        // 当前页的数量已经小于from
        if (search == null) {
            return Result.ok();
        }
        
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> shops = search
                .getContent()
                .stream()
                .skip(from)
                .collect(Collectors.toList());
        
        // 存储shop对应距离的map
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = search.getContent();
        if (list.size() <= from) {
            return Result.ok();
        }
        
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        
        List<Long> ids = list.stream()
                .skip(from)
                .map(shop -> {
                    Long id = Long.valueOf(shop.getContent().getName());
                    distanceMap.put(shop.getContent().getName(), shop.getDistance());
                    return id;
                })
                .collect(Collectors.toList());
        
        
        // 根据id从数据库中查shop
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shopList = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idsStr + ")")
                .list();
        
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        
        return Result.ok(shopList);
    }
}
