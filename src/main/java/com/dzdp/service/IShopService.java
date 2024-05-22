package com.dzdp.service;

import com.dzdp.service.dto.Result;
import com.dzdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dzdp.utils.RedisData;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

//    RedisData saveRedisData(long l, long l1);

    // 在原来对象的基础上添加逻辑过期时间字段, 用类组合的方式组合逻辑过期字段
    RedisData saveRedisData(Long id, Long expireTime);

    Result querShopByType(Integer typeId, Integer current, Double x, Double y);
}
