package com.dzdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.dzdp.service.dto.Result;
import com.dzdp.entity.ShopType;
import com.dzdp.mapper.ShopTypeMapper;
import com.dzdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.dzdp.utils.RedisConstants.CACHE_SHOP_TYPE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result selectList() {
        String cacheShopType = CACHE_SHOP_TYPE;
        // 返回的是一个list集合
        // 先从redis缓存中查找
        List<String> jsonString = stringRedisTemplate.opsForList().range(cacheShopType, 0L, -1);
        List<ShopType> shopTypeList1 = new ArrayList<>();
        if(jsonString!= null){
            shopTypeList1 = JSONUtil.toList(jsonString.toString(), ShopType.class);
        }
        
        if (!shopTypeList1.isEmpty()) {
            List<ShopType> shopTypeList = new ArrayList<>();
            for (String s : jsonString) {
                shopTypeList.add(JSONUtil.toBean(s,ShopType.class));
            }
//            System.out.println("===============================");
            return Result.ok(shopTypeList);
        }
        // 没有则调用数据库查询
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
//        String jsonStr = JSONUtil.toJsonStr(shopTypeList.toArray());
        // 查询到的数据写入redis缓存
        for (ShopType shopType : shopTypeList) {
            stringRedisTemplate.opsForList().leftPush(cacheShopType,JSONUtil.toJsonStr(shopType));
        }
//        stringRedisTemplate.opsForList().leftPushAll(cacheShopType,  (shopTypeList));
        // 返回数据
        return Result.ok(shopTypeList);

    }
}
