package com.dzdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.dzdp.entity.VoucherInfo;
import com.dzdp.rabbitmq.Producer;
import com.dzdp.service.dto.Result;
import com.dzdp.entity.VoucherOrder;
import com.dzdp.mapper.VoucherOrderMapper;
import com.dzdp.service.ISeckillVoucherService;
import com.dzdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzdp.utils.RedisIdWorker;
import com.dzdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private Producer producer;

    @Resource
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override

    public Result seckillVoucher(Long voucherId){
        Long userId = UserHolder.getUser().getId();

        // 得到lua脚本执行的返回值
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());

        int r = result.intValue();
        if(r != 0){
            return Result.fail(r == 1? "库存不足" : "用户已经下过单");
        }

        // TODO: 将订单加入阻塞队列
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //交给spring管理
        Long order = redisIdWorker.createId("order");
        
        VoucherInfo voucherInfo = new VoucherInfo();
        voucherInfo.setUserId(userId);
        voucherInfo.setVoucherId(voucherId);
        voucherInfo.setOrderId(order);
        producer.sendOrder(JSONUtil.toJsonStr(voucherInfo));
        
        return Result.ok(order);
    }
    // 原本的下单操作

//    public Result seckillVoucher(Long voucherId){
//        //查询优惠券信息
//        SeckillVoucher sk = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始
//        if (sk.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//
//        //判断秒杀是否结束
//        if (sk.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//
//        //判断库存是否充足
//        if (sk.getStock()<1) {
//            return Result.fail("库存不足");
//        }
//
//        Long id = UserHolder.getUser().getId();
//        // id.toString()仍然是new了一个对象, 加锁没用, 调用intern()方法,是从常量池中拿值
////        synchronized (id.toString().intern()){
////            // 下面的事务要想生效必须用代理对象,
////            // 先拿到当前对象的代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            //交给spring管理
////            return proxy.createVoucherOrder(voucherId);
////        }
//        // 利用分布式锁来解决集群问题的并发
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
//        // 使用redisson
//        RLock lock = redissonClient.getLock("lock:order" + id);
//
//        boolean getLock = lock.tryLock();
//        if (!getLock) {
//            return Result.fail("一个用户只能买一张优惠券");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //交给spring管理
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();// 释放锁
//        }
//    }

    @Transactional // 涉及到了多表, 加上事务
    public Result createVoucherOrder(Long voucherId,Long id,Long orderId) {
        // 获取用户id
//        Long id = UserHolder.getUser().getId();
        // 一人一单
        int count = query().eq("user_id", id).eq("voucher_id", voucherId).count();

        if(count > 0){
            return Result.fail("一人只能买一张优惠券");
        }

        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .gt("stock",0)
                .eq("voucher_id", voucherId)
                .update();

        if(!success){
            // 没有扣减成功, 说明库存不足
            return Result.fail("库存不足");
        }

        //创键订单
        // 订单id, 用户id, 代金券id
        VoucherOrder voucherOrder = new VoucherOrder();

        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(id);
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
