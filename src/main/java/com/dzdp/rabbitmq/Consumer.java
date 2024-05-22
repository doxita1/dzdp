package com.dzdp.rabbitmq;


import cn.hutool.json.JSONUtil;
import com.dzdp.entity.VoucherInfo;
import com.dzdp.service.IVoucherOrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class Consumer {
    
    @Resource
    IVoucherOrderService voucherOrderService;
    
    @RabbitListener(queues = "voucher.order")
    public void receive(String voucherInfo) {
        VoucherInfo info = JSONUtil.toBean(voucherInfo, VoucherInfo.class);
        Long userId = info.getUserId();
        Long voucherId = info.getVoucherId();
        Long orderId = info.getOrderId();
        voucherOrderService.createVoucherOrder(voucherId, userId,orderId);
    }
}
