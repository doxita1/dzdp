package com.dzdp.rabbitmq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Component
public class Producer {
    
    @Resource
    private RabbitTemplate rabbitTemplate;
    
    public void sendOrder(String voucherInfo) {
        rabbitTemplate.convertAndSend("orderExchange", "order", voucherInfo);
    }
    
}
