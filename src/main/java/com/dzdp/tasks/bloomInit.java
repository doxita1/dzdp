package com.dzdp.tasks;


import com.dzdp.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
@Slf4j
public class bloomInit {
    
    @Resource
    ShopMapper shopMapper;
    
    @Resource
    RedissonClient redissonClient;
    
    @Scheduled(fixedRate = 60 * 1000)
    public void initBloomFilter(){
        RBloomFilter<Object> shopId = redissonClient.getBloomFilter("shopId");
        List<Long> longs = shopMapper.selectAllIds();
        boolean success = shopId.add(longs);
        if(success){
            log.info("添加成功");
        }else{
            log.error("添加失败");
        }
    }
}
