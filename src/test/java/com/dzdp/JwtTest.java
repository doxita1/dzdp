package com.dzdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.dzdp.entity.User;
import com.dzdp.service.IUserService;
import com.dzdp.service.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
@Slf4j
public class JwtTest {
    public static final String SECRET_KEY = "my secret";
    @Resource
    IUserService userService;
    
    
    @Test
    public void createJwt() {
        // 创建签名器
        JWTSigner signer = JWTSignerUtil.hs256(SECRET_KEY.getBytes());
        
        // 自定义的有效负载
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", "123456");
        payload.put("role", "admin");
        
        // 设置过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, 1); // 1小时后过期
        
        // 生成 JWT
        String token = JWT.create()
                .addPayloads(payload)
                .setExpiresAt(calendar.getTime()) // 设置过期时间
                .sign(signer); // 签名
        
        System.out.println("Generated Token: " + token);
        
        // 验证 JWT
        boolean isValid = JWTUtil.verify(token, signer);
        System.out.println("Is Token Valid: " + isValid);
        
        // 解析 JWT
        JWT parsedJWT = JWTUtil.parseToken(token);
        System.out.println("Parsed JWT: " + parsedJWT);
        System.out.println("Subject: " + parsedJWT.getPayload("userId"));
        System.out.println("Role: " + parsedJWT.getPayload("role"));
    }
    
    @Test
    public void testBeanToMap(){
        User user = userService.getById(1);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        
        Map<String, Object> id = BeanUtil.beanToMap(userDTO
                , new HashMap<>()
                , CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName, filedValue) -> {
                            if (!filedName.equals("id")) {
                                return filedValue.toString();
                            }
                            return null;
                        }));
        
        System.out.println(id);
        
    }
}

