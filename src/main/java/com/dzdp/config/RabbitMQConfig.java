package com.dzdp.config;


import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {
    
    @Bean
    Queue queue() {
        return new Queue("voucher.order", true);
    }
    
    @Bean
    DirectExchange exchange() {
        return new DirectExchange("orderExchange");
    }
    
    @Bean
    Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("order");
    }
    
}
