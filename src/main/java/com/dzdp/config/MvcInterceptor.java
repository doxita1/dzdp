package com.dzdp.config;

import com.dzdp.utils.LoginInterceptor;
import com.dzdp.utils.ReflashTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;


@Configuration
public class MvcInterceptor implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    // 判断登录与否的拦截器
    public void addInterceptors(InterceptorRegistry registry) {
       registry.addInterceptor(new LoginInterceptor())
               .excludePathPatterns(
                       "/shop/**",
                       "/voucher/**",
                       "/shop-type/**",
                       "/upload/**",
                       "/blog/hot",
                       "/user/code",
                       "/user/login",
                       "/user/me"
               ).order(1);
       // 用来更新token时间
       registry.addInterceptor(new ReflashTokenInterceptor(stringRedisTemplate)).order(0).addPathPatterns("/**");
       // 用order来设置拦截器的优先级,
    }
}
