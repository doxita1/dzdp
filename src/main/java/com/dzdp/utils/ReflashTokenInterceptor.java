package com.dzdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.dzdp.service.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dzdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.dzdp.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
public class ReflashTokenInterceptor implements HandlerInterceptor {
    
    public static final String SECRET_KEY = "my secret";
    /*
        不能直接注入, 这是自定义的对象, 没有交给spring容器管理, 从MVCconfig中注入
        然后调用构造方法来注入stringRedisTemplate对象
     */
    private StringRedisTemplate stringRedisTemplate;

    public ReflashTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // TODO 获得header中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            // 放行
            return true;
        }
        String key = LOGIN_USER_KEY + token;

        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            // 放行
            return true;
        }
        // 验证 JWT
        JWTSigner signer = JWTSignerUtil.hs256(SECRET_KEY.getBytes());
//        log.error(token);
        boolean isValid = JWTUtil.verify(token, signer);
//        if(isValid){
//            log.error("Is Token Valid: false");
//            throw new RuntimeException("用户验证失败");
//        }
        // 解析 JWT
        JWT parsedJWT = JWTUtil.parseToken(token);
        JSONObject payloads = parsedJWT.getPayloads();
        UserDTO bean = JSONUtil.toBean(payloads, UserDTO.class);
        // TODO 存在, 保存到threadLocal中(方便其他功能得到用户)
//        UserHolder.saveUser(BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false));
        UserHolder.saveUser(bean);
        // TODO 刷新redis中的有效时间
        stringRedisTemplate.expire(key, LOGIN_USER_TTL , TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        response.setHeader("authorization",null);
        UserHolder.removeUser();
    }
}
